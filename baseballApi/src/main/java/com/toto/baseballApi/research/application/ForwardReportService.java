package com.toto.baseballApi.research.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOdds;
import com.toto.baseballApi.pick.domain.ForwardPick;
import com.toto.baseballApi.pick.domain.ForwardPickRepository;
import com.toto.baseballApi.pick.domain.ForwardPickStatus;
import com.toto.baseballApi.pick.domain.LegTally;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickUniverse;
import com.toto.baseballApi.research.domain.BacktestMetrics;
import com.toto.baseballApi.research.domain.DayOutcome;
import com.toto.baseballApi.research.domain.ForwardReport;

/**
 * Reads the forward test's accumulated record and scores it against the declared goal.
 *
 * <p>Lives in {@code research} rather than {@code pick} because judging a candidate is what
 * {@code research} is for — it owns {@code ExperimentGoal}, {@code BacktestMetrics}, and the ledger,
 * and {@code pick} must not depend on it. {@code pick} owns the picks; this owns the verdict.
 *
 * <p>Writes nothing, like everything else in this feature. Promoting a forward result into
 * {@code pick_mstr} would stay a separate deliberate act, and a report that mutated the thing it
 * measures would destroy the append-only guarantee the whole test rests on.
 */
@Service
public class ForwardReportService {

    /** 승 in the 3-way and 2-way markets alike; the side the away-baseline control does <em>not</em> take. */
    private static final String AWAY_WIN = "패";

    private final ForwardPickRepository forwardPickRepository;
    private final BaseballResultRepository baseballResultRepository;
    private final ResearchProperties properties;
    private final Map<String, String> algorithmNamesByCode;

    public ForwardReportService(
            ForwardPickRepository forwardPickRepository,
            BaseballResultRepository baseballResultRepository,
            ResearchProperties properties,
            List<PickAlgorithm> algorithms) {
        this.forwardPickRepository = forwardPickRepository;
        this.baseballResultRepository = baseballResultRepository;
        this.properties = properties;
        this.algorithmNamesByCode = algorithms.stream()
                .collect(Collectors.toMap(PickAlgorithm::code, PickAlgorithm::name));
    }

    /**
     * One report per {@code (algorithmCode, userName)} on record, most slips first. Every candidate
     * under forward test is listed, including ones with nothing settled yet — "we are 3 days in with
     * nothing resolved" is the answer the operator needs on those days.
     */
    @Transactional(readOnly = true)
    public List<ForwardReport> reports() {
        List<ForwardPick> all = forwardPickRepository.findAll();
        if (all.isEmpty()) {
            return List.of();
        }

        // Loaded once for every candidate: the settled legs point at result rows, and the coverage
        // check needs the days that have finished since the test began.
        Map<Integer, BaseballResult> resultsById = resultsById(all);
        List<String> playedYmds = playedYmdsFrom(earliestYmd(all));

        record Key(String algorithmCode, String userName) {
        }
        Map<Key, List<ForwardPick>> byCandidate = all.stream().collect(Collectors.groupingBy(
                pick -> new Key(pick.algorithmCode(), pick.userName()),
                LinkedHashMap::new, Collectors.toList()));

        return byCandidate.entrySet().stream()
                .map(entry -> report(
                        entry.getKey().algorithmCode(), entry.getKey().userName(),
                        entry.getValue(), resultsById, playedYmds))
                .sorted(Comparator.comparingInt(ForwardReport::slipCount).reversed())
                .toList();
    }

    private ForwardReport report(
            String algorithmCode, String userName, List<ForwardPick> picks,
            Map<Integer, BaseballResult> resultsById, List<String> playedYmds) {

        BacktestMetrics metrics = BacktestMetrics.from(settledDays(picks));

        List<String> pendingYmds = picks.stream()
                .filter(pick -> pick.status() == ForwardPickStatus.PENDING)
                .map(ForwardPick::ymd)
                .distinct()
                .sorted()
                .toList();

        return new ForwardReport(
                algorithmCode,
                algorithmNamesByCode.getOrDefault(algorithmCode, algorithmCode),
                userName,
                picks.stream().map(ForwardPick::paramSignature).distinct().sorted().toList(),
                picks.size(),
                (int) picks.stream().filter(ForwardPick::settled).count(),
                pendingYmds.isEmpty() ? 0 : (int) picks.stream()
                        .filter(pick -> pick.status() == ForwardPickStatus.PENDING).count(),
                pendingYmds,
                metrics,
                properties.goal().toDomain().evaluate(metrics),
                awayBaseline(picks, resultsById),
                backedSideCounts(picks),
                coverage(picks, playedYmds));
    }

    /**
     * Settled slips folded into one {@link DayOutcome} per date, which is the unit
     * {@link BacktestMetrics} treats as a betting day. Grouped by {@code ymd} rather than by slot so
     * {@code bettingDayCount} means days — the same convention the non-staking evaluators use, so the
     * {@code minBettingDayCount} gate compares like with like.
     */
    private List<DayOutcome> settledDays(List<ForwardPick> picks) {
        Map<String, List<ForwardPick>> byYmd = new TreeMap<>();
        for (ForwardPick pick : picks) {
            if (pick.settled()) {
                byYmd.computeIfAbsent(pick.ymd(), key -> new ArrayList<>()).add(pick);
            }
        }

        List<DayOutcome> outcomes = new ArrayList<>();
        for (Map.Entry<String, List<ForwardPick>> day : byYmd.entrySet()) {
            int hits = 0;
            BigDecimal inputTotal = BigDecimal.ZERO;
            BigDecimal outputTotal = BigDecimal.ZERO;
            BigDecimal benchmarkTotal = BigDecimal.ZERO;
            LegTally legs = LegTally.EMPTY;
            for (ForwardPick pick : day.getValue()) {
                if (pick.hit()) {
                    hits++;
                }
                inputTotal = inputTotal.add(pick.inputMoney());
                outputTotal = outputTotal.add(
                        pick.outputMoney() == null ? BigDecimal.ZERO : pick.outputMoney());
                BigDecimal benchmark = pick.benchmarkOutputMoney();
                if (benchmark != null) {
                    benchmarkTotal = benchmarkTotal.add(benchmark);
                }
                legs = legs.plus(pick.legTally());
            }
            outcomes.add(new DayOutcome(day.getKey(), day.getValue().size(), hits,
                    inputTotal, outputTotal, benchmarkTotal, legs));
        }
        return outcomes;
    }

    /**
     * The control: back the away side on every settled leg, at that leg's own published away price.
     *
     * <p>Deliberately reads {@code baseball_result} rather than the forward record, because the record
     * only stores the side that was actually backed. That is a re-derivation and it is safe here for
     * the reason §16 gives: reading a <em>published</em> price after the fact measures, it does not
     * select. What it must never touch is {@code totalDiv}, and it does not.
     */
    private LegTally awayBaseline(
            List<ForwardPick> picks, Map<Integer, BaseballResult> resultsById) {
        int count = 0;
        int hitCount = 0;
        BigDecimal payoutTotal = BigDecimal.ZERO;
        BigDecimal benchmarkTotal = BigDecimal.ZERO;
        BigDecimal payoutSquareTotal = BigDecimal.ZERO;

        for (ForwardPick pick : picks) {
            if (!pick.settled()) {
                continue;
            }
            for (ForwardPick.ForwardPickLeg leg : pick.legs()) {
                BaseballResult game = leg.resultId() == null ? null : resultsById.get(leg.resultId());
                if (game == null) {
                    continue;
                }
                Double awayOdds = awayOdds(game);
                if (awayOdds == null) {
                    continue;
                }
                count++;
                benchmarkTotal = benchmarkTotal.add(leg.benchmarkValue());
                if (AWAY_WIN.equals(game.totalResult())) {
                    hitCount++;
                    BigDecimal payout = BigDecimal.valueOf(awayOdds);
                    payoutTotal = payoutTotal.add(payout);
                    payoutSquareTotal = payoutSquareTotal.add(payout.multiply(payout));
                }
            }
        }
        return count == 0 ? null
                : new LegTally(count, hitCount, payoutTotal, benchmarkTotal, payoutSquareTotal);
    }

    /** The published away price, from whichever market this row is in. */
    private static Double awayOdds(BaseballResult game) {
        ThreeWayOdds threeWay = game.publishedOdds();
        if (threeWay != null) {
            return threeWay.away();
        }
        TwoWayOdds twoWay = game.publishedTwoWayOdds();
        return twoWay == null ? null : twoWay.away();
    }

    private static Map<String, Integer> backedSideCounts(List<ForwardPick> picks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ForwardPick pick : picks) {
            for (ForwardPick.ForwardPickLeg leg : pick.legs()) {
                counts.merge(leg.totalResult(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static ForwardReport.Coverage coverage(
            List<ForwardPick> picks, List<String> playedYmds) {
        Set<String> pickedYmds = picks.stream().map(ForwardPick::ymd)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (pickedYmds.isEmpty()) {
            return ForwardReport.Coverage.NONE;
        }
        String firstYmd = pickedYmds.stream().min(Comparator.naturalOrder()).orElseThrow();
        String lastYmd = pickedYmds.stream().max(Comparator.naturalOrder()).orElseThrow();

        List<String> played = playedYmds.stream()
                .filter(ymd -> ymd.compareTo(firstYmd) >= 0)
                .toList();
        List<String> missed = played.stream()
                .filter(ymd -> !pickedYmds.contains(ymd))
                .toList();

        return new ForwardReport.Coverage(
                firstYmd, lastYmd, played.size(), played.size() - missed.size(), missed);
    }

    /**
     * Days with finished games from {@code fromYmd} on. Bounded by the forward test's own start date,
     * so this stays a handful of days' worth of rows however long the history gets.
     */
    private List<String> playedYmdsFrom(String fromYmd) {
        if (fromYmd == null) {
            return List.of();
        }
        return baseballResultRepository.findByGameTypeAndTournamentsAndYmdBetween(
                        PickUniverse.TWO_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS, fromYmd, "999999")
                .stream()
                .map(BaseballResult::ymd)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Integer, BaseballResult> resultsById(List<ForwardPick> picks) {
        List<Integer> ids = picks.stream()
                .flatMap(pick -> pick.legs().stream())
                .map(ForwardPick.ForwardPickLeg::resultId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        return ids.isEmpty() ? Map.of()
                : baseballResultRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(BaseballResult::id, Function.identity(), (a, b) -> a));
    }

    private static String earliestYmd(List<ForwardPick> picks) {
        return picks.stream().map(ForwardPick::ymd).min(Comparator.naturalOrder()).orElse(null);
    }
}
