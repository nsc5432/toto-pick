package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.FixtureKey;
import com.toto.baseballApi.matchschedule.domain.MatchSchedule;
import com.toto.baseballApi.matchschedule.domain.MatchScheduleRepository;
import com.toto.baseballApi.pick.domain.AlgorithmParams;
import com.toto.baseballApi.pick.domain.BackedPrice;
import com.toto.baseballApi.pick.domain.ForwardPick;
import com.toto.baseballApi.pick.domain.ForwardPickRepository;
import com.toto.baseballApi.pick.domain.ForwardPickStatus;
import com.toto.baseballApi.pick.domain.MarketPairIndex;
import com.toto.baseballApi.pick.domain.MartingaleStaking;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickSelection;
import com.toto.baseballApi.pick.domain.PickSlip;
import com.toto.baseballApi.pick.domain.PickUniverse;
import com.toto.baseballApi.pick.domain.SlipSelectionInput;
import com.toto.baseballApi.pick.domain.StakingAlgorithm;
import com.toto.baseballApi.pick.domain.TeamFormIndex;

/**
 * Produces the picks for a day that has not been played yet, and records them immutably.
 *
 * <p>This is the counterpart to {@code PickSimulationService} and {@code BacktestService}, and the
 * only one of the three whose output cannot be contaminated by the choice of parameters: it selects
 * from {@code match_schedule}, which holds fixtures with no outcome at all. Four "goal achieved"
 * verdicts have already turned out to be artefacts of measuring on days that were available while
 * tuning (docs/statistical-model-design.md §10, §15); this path exists so the next verdict does not
 * have to be one.
 *
 * <p>Selection itself is unchanged — the same {@link PickAlgorithm} beans, the same slot rule, the
 * same {@link MarketPairIndex}. What differs is that nothing is settled here, because there is
 * nothing to settle against yet.
 */
@Service
public class ForwardPickService {

    // Same values as the research pipeline's BacktestSettings.DEFAULT, restated here because `pick`
    // must not depend on `research`. Only used for knobs the frozen params leave unset.
    private static final int DEFAULT_NUM = 20;
    private static final double DEFAULT_X = 1.80;
    private static final double DEFAULT_Y = 2.50;
    private static final int DEFAULT_COMBINED_N = 3;

    private final MatchScheduleRepository matchScheduleRepository;
    private final BaseballResultRepository baseballResultRepository;
    private final ForwardPickRepository forwardPickRepository;
    private final Map<String, PickAlgorithm> algorithmsByCode;

    public ForwardPickService(
            MatchScheduleRepository matchScheduleRepository,
            BaseballResultRepository baseballResultRepository,
            ForwardPickRepository forwardPickRepository,
            List<PickAlgorithm> algorithms) {
        this.matchScheduleRepository = matchScheduleRepository;
        this.baseballResultRepository = baseballResultRepository;
        this.forwardPickRepository = forwardPickRepository;
        this.algorithmsByCode = algorithms.stream()
                .collect(Collectors.toMap(PickAlgorithm::code, Function.identity()));
    }

    @Transactional
    public ForwardPickResult generate(GenerateForwardPicksCommand command) {
        PickAlgorithm algorithm = algorithmsByCode.get(command.algorithmCode());
        if (algorithm == null) {
            throw new IllegalArgumentException("Unknown algorithm code: " + command.algorithmCode());
        }
        AlgorithmParams params = command.params() == null
                ? AlgorithmParams.empty()
                : AlgorithmParams.of(command.params());

        List<MatchSchedule> scheduled = matchScheduleRepository.findByYmdAndGameTypes(
                command.ymd(), List.of(PickUniverse.THREE_WAY_GAME_TYPE, PickUniverse.TWO_WAY_GAME_TYPE));
        List<MatchSchedule> eligible = scheduled.stream()
                .filter(s -> PickUniverse.TOURNAMENTS.contains(s.tournament()))
                .toList();

        // The schedule id is only a handle for selection; the pick records the fixture itself,
        // because the baseball_result row it will settle against does not exist yet.
        Map<Integer, MatchSchedule> byScheduleId = eligible.stream()
                .collect(Collectors.toMap(MatchSchedule::id, Function.identity()));

        List<BaseballResult> threeWayFixtures = eligible.stream()
                .filter(s -> PickUniverse.THREE_WAY_GAME_TYPE.equals(s.gameType()))
                .map(MatchSchedule::asFixture)
                .toList();
        MarketPairIndex marketPairs = MarketPairIndex.build(eligible.stream()
                .filter(s -> PickUniverse.TWO_WAY_GAME_TYPE.equals(s.gameType()))
                .map(MatchSchedule::asFixture)
                .toList());

        // Form comes from finished games only — strictly before the day being picked, as always.
        List<BaseballResult> history = baseballResultRepository
                .findByGameTypeAndTournamentsAndYmdBefore(
                        PickUniverse.TWO_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS, command.ymd());
        TeamFormIndex formIndex = TeamFormIndex.build(history);

        MartingaleStaking staking = algorithm instanceof StakingAlgorithm stakingAlgorithm
                ? replayStakingSession(stakingAlgorithm, params, command.userName())
                : null;

        List<ForwardPick> written = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<String, List<BaseballResult>> slot : slots(threeWayFixtures).entrySet()) {
            // Seeded with the shared defaults rather than zeros: params is meant to carry the frozen
            // configuration, but a knob it happens to omit should behave the way it does everywhere
            // else, not blow up (num = 0 would throw inside TeamFormIndex).
            SlipSelectionInput input = new SlipSelectionInput(
                    command.ymd(), DEFAULT_NUM, DEFAULT_X, DEFAULT_Y, DEFAULT_COMBINED_N,
                    slot.getValue(), history, formIndex, marketPairs, AlgorithmParams.empty())
                    .withParams(params);

            List<PickSlip> slips = algorithm.selectSlips(input);
            // A staking algorithm bets one slip per slot by design (마틴게일 R2).
            if (staking != null && slips.size() > 1) {
                slips = List.of(slips.get(0));
            }

            for (int slipNo = 0; slipNo < slips.size(); slipNo++) {
                if (forwardPickRepository.existsSlip(
                        algorithm.code(), command.userName(), command.ymd(), slot.getKey(), slipNo)) {
                    skipped++;
                    continue;
                }
                ForwardPick pick = toForwardPick(
                        slips.get(slipNo), slipNo, slot.getKey(), byScheduleId,
                        algorithm, params, command, staking);
                if (pick != null) {
                    written.add(forwardPickRepository.append(pick));
                }
            }
        }
        return new ForwardPickResult(
                command.ymd(), algorithm.code(), params.signature(),
                threeWayFixtures.size(), skipped, written);
    }

    /**
     * The picks on record, newest day first, optionally narrowed. Read-only.
     *
     * <p>Unpaginated: the record grows by a handful of slips a day and is meant to be read whole —
     * a forward test's value is the accumulation, not the last page of it.
     */
    @Transactional(readOnly = true)
    public List<ForwardPick> picks(String algorithmCode, String userName, String status) {
        return forwardPickRepository.findAll().stream()
                .filter(pick -> algorithmCode == null || algorithmCode.isBlank()
                        || algorithmCode.equals(pick.algorithmCode()))
                .filter(pick -> userName == null || userName.isBlank()
                        || userName.equals(pick.userName()))
                .filter(pick -> status == null || status.isBlank()
                        || status.equalsIgnoreCase(pick.status().name()))
                .sorted(Comparator.comparing(ForwardPick::ymd).reversed()
                        .thenComparing(ForwardPick::bucket)
                        .thenComparing(ForwardPick::slipNo))
                .toList();
    }

    /** Display name of the algorithm that produced a pick, for read models. */
    public String algorithmName(String algorithmCode) {
        PickAlgorithm algorithm = algorithmsByCode.get(algorithmCode);
        return algorithm == null ? algorithmCode : algorithm.name();
    }

    /** One {@code (ymd, 조합버킷)} slot per entry, in the order the day's games resolve. */
    private Map<String, List<BaseballResult>> slots(List<BaseballResult> fixtures) {
        Map<String, List<BaseballResult>> bySlot = PickUniverse.distinctFixtures(fixtures).stream()
                .collect(Collectors.groupingBy(
                        game -> PickUniverse.combinationBucket(game.tournament()),
                        LinkedHashMap::new, Collectors.toList()));
        return bySlot.entrySet().stream()
                .sorted(Comparator.comparing(entry -> earliestTm(entry.getValue())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static String earliestTm(List<BaseballResult> games) {
        return games.stream().map(BaseballResult::tm).filter(tm -> tm != null)
                .min(Comparator.naturalOrder()).orElse("");
    }

    private ForwardPick toForwardPick(
            PickSlip slip, int slipNo, String bucket, Map<Integer, MatchSchedule> byScheduleId,
            PickAlgorithm algorithm, AlgorithmParams params,
            GenerateForwardPicksCommand command, MartingaleStaking staking) {

        List<ForwardPick.ForwardPickLeg> legs = new ArrayList<>();
        BigDecimal combinedOdds = BigDecimal.ONE;
        int legNo = 0;
        for (PickSelection selection : slip.selections()) {
            MatchSchedule fixture = byScheduleId.get(selection.resultId());
            if (fixture == null) {
                // Selection pointed at something outside this day's schedule — refuse to guess.
                return null;
            }
            BackedPrice price = selection.price() != null
                    ? selection.price()
                    : publishedPrice(fixture, selection.predictedTotalResult());
            if (price == null) {
                return null;
            }
            legs.add(new ForwardPick.ForwardPickLeg(
                    legNo++, FixtureKey.of(fixture.asFixture()), fixture.gameType(),
                    selection.predictedTotalResult(),
                    BigDecimal.valueOf(price.odds()).setScale(2, java.math.RoundingMode.HALF_UP),
                    BigDecimal.valueOf(price.overround()).setScale(6, java.math.RoundingMode.HALF_UP),
                    null, null));
            combinedOdds = combinedOdds.multiply(BigDecimal.valueOf(price.odds()));
        }
        if (legs.isEmpty()) {
            return null;
        }
        combinedOdds = combinedOdds.setScale(2, java.math.RoundingMode.CEILING);

        BigDecimal stake = command.inputMoney();
        if (staking != null) {
            stake = staking.nextStake(combinedOdds);
            if (stake == null) {
                return null;
            }
            // The session advances only once the game resolves; settlement replays it from scratch,
            // so nothing is told here about an outcome that does not exist.
        }

        return new ForwardPick(
                null, algorithm.code(), params.signature(), command.userName(),
                command.ymd(), bucket, slipNo, stake, combinedOdds,
                ForwardPickStatus.PENDING, null, LocalDateTime.now(), null, legs);
    }

    /**
     * The fixture's own published price for the backed side — the fallback for an algorithm that did
     * not attach one. Reads whichever market the listing is in, since a 3-way row publishes all three
     * slots and a 2-way row only two.
     */
    private static BackedPrice publishedPrice(MatchSchedule fixture, String predicted) {
        BaseballResult listing = fixture.asFixture();
        var threeWay = listing.publishedOdds();
        if (threeWay != null) {
            double backed = switch (predicted) {
                case "승" -> threeWay.home();
                case "1" -> threeWay.draw();
                case "패" -> threeWay.away();
                default -> throw new IllegalArgumentException("Unknown predicted result: " + predicted);
            };
            return new BackedPrice(backed, threeWay.overround());
        }
        var twoWay = listing.publishedTwoWayOdds();
        if (twoWay == null) {
            return null;
        }
        double backed = switch (predicted) {
            case "승", "언더", "홀" -> twoWay.home();
            case "패", "오버", "짝" -> twoWay.away();
            default -> throw new IllegalArgumentException("Unknown predicted result: " + predicted);
        };
        return new BackedPrice(backed, twoWay.overround());
    }

    /**
     * Rebuilds the martingale session by replaying every settled forward pick in order.
     *
     * <p>Deliberately derived rather than stored: a stored counter drifts the moment a settlement is
     * re-run or a pick is voided, and the sequence's whole meaning is that it never silently resets
     * (마틴게일 R5). Pending picks are skipped — a bet whose game has not resolved has not yet won or
     * lost, so it cannot advance the sequence.
     */
    private MartingaleStaking replayStakingSession(
            StakingAlgorithm algorithm, AlgorithmParams params, String userName) {
        MartingaleStaking staking = algorithm.newStakingSession(params);
        for (ForwardPick pick : forwardPickRepository.findByAlgorithm(algorithm.code(), userName)) {
            if (pick.status() == ForwardPickStatus.SETTLED) {
                staking.settle(pick.inputMoney(), pick.hit());
            }
        }
        return staking;
    }
}
