package com.toto.baseballApi.research.application;

import java.math.BigDecimal;
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
import com.toto.baseballApi.pick.domain.AlgorithmParams;
import com.toto.baseballApi.pick.domain.LegTally;
import com.toto.baseballApi.pick.domain.MartingaleStaking;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickBacktester;
import com.toto.baseballApi.pick.domain.PickUniverse;
import com.toto.baseballApi.pick.domain.SettledSlip;
import com.toto.baseballApi.pick.domain.SlipSelectionInput;
import com.toto.baseballApi.pick.domain.StakingAlgorithm;
import com.toto.baseballApi.pick.domain.TeamFormIndex;
import com.toto.baseballApi.research.domain.BacktestMetrics;
import com.toto.baseballApi.research.domain.BacktestWindow;
import com.toto.baseballApi.research.domain.DayOutcome;

import lombok.RequiredArgsConstructor;

/**
 * Scores one algorithm+params over one window, without writing anything.
 *
 * <p>This is the deliberate counterpart to {@code PickSimulationService}: same universe, same
 * rolling-window rule, same settlement (both go through {@code PickBacktester}) — but nothing is
 * persisted. A sweep of hundreds of candidates must not leave hundreds of throwaway
 * {@code pick_mstr} rows behind, and only the winner is worth committing, via the existing
 * {@code POST /api/picks/simulate}.
 */
@Service
@RequiredArgsConstructor
public class BacktestService {

    private final BaseballResultRepository baseballResultRepository;

    /**
     * Loads every game the range needs in two queries and pre-indexes it. Call once per search and
     * hand the result to every {@link #evaluate} call.
     */
    @Transactional(readOnly = true)
    public BacktestData load(String bgngYmd, String endYmd) {
        if (bgngYmd.compareTo(endYmd) > 0) {
            throw new IllegalArgumentException("bgngYmd must not be after endYmd");
        }

        List<BaseballResult> targetGames = baseballResultRepository.findByGameTypeAndTournamentsAndYmdBetween(
                PickUniverse.THREE_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS, bgngYmd, endYmd);
        List<BaseballResult> history = baseballResultRepository
                .findByGameTypeAndTournamentsAndYmdBefore(
                        PickUniverse.TWO_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS, endYmd)
                .stream()
                .sorted(Comparator.comparing(BaseballResult::ymd).thenComparing(BaseballResult::tm))
                .toList();

        record DayKey(Integer year, Integer round, String ymd) {
        }
        Map<DayKey, List<BaseballResult>> gamesByDay = targetGames.stream()
                .collect(Collectors.groupingBy(g -> new DayKey(g.year(), g.round(), g.ymd())));

        List<BacktestData.PickDay> days = gamesByDay.keySet().stream()
                .sorted(Comparator.comparing(DayKey::ymd)
                        .thenComparing(DayKey::year)
                        .thenComparing(DayKey::round))
                .map(key -> pickDay(key.ymd(), gamesByDay.get(key), history))
                .toList();

        // The staking unit: one (ymd, 조합버킷) slot, ordered by the slot's earliest game time so
        // the martingale fold follows the schedule instead of a hard-coded "MLB first". A slot spans
        // 회차, so it is deduplicated — the same game listed in two 회차 must not fill both legs.
        record SlotKey(String ymd, String bucket) {
        }
        Map<SlotKey, List<BaseballResult>> gamesBySlot =
                PickUniverse.distinctFixtures(targetGames).stream()
                        .collect(Collectors.groupingBy(g -> new SlotKey(
                                g.ymd(), PickUniverse.combinationBucket(g.tournament()))));

        List<BacktestData.PickDay> stakingSlots = gamesBySlot.keySet().stream()
                .sorted(Comparator.comparing(SlotKey::ymd)
                        .thenComparing(key -> earliestTm(gamesBySlot.get(key)))
                        .thenComparing(SlotKey::bucket))
                .map(key -> pickDay(key.ymd(), gamesBySlot.get(key), history))
                .toList();

        return new BacktestData(days, stakingSlots, history, TeamFormIndex.build(history));
    }

    private BacktestData.PickDay pickDay(
            String ymd, List<BaseballResult> games, List<BaseballResult> history) {
        return new BacktestData.PickDay(
                ymd, games,
                games.stream().collect(Collectors.toMap(
                        BaseballResult::id, Function.identity(), (a, b) -> a, LinkedHashMap::new)),
                historyPrefixLength(history, ymd));
    }

    private static String earliestTm(List<BaseballResult> games) {
        return games.stream()
                .map(BaseballResult::tm)
                .filter(tm -> tm != null)
                .min(Comparator.naturalOrder())
                .orElse("");
    }

    /** Index of the first history game on or after {@code ymd} — i.e. how many strictly precede it. */
    private int historyPrefixLength(List<BaseballResult> historyByYmdAsc, String ymd) {
        int low = 0;
        int high = historyByYmdAsc.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (historyByYmdAsc.get(mid).ymd().compareTo(ymd) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public BacktestMetrics evaluate(
            PickAlgorithm algorithm, AlgorithmParams params, BacktestData data,
            BacktestWindow window, BacktestSettings settings) {

        // One staking session per evaluate() call: candidates sweep on parallel threads, but each
        // gets its own session here, and the day loop below stays a sequential ymd-ordered fold —
        // both are what makes a path-dependent stake rule safe inside a parallel sweep.
        MartingaleStaking staking = algorithm instanceof StakingAlgorithm stakingAlgorithm
                ? stakingAlgorithm.newStakingSession(params)
                : null;

        // A staking algorithm is folded over slots, not days — one ymd can contribute two of them.
        List<BacktestData.PickDay> units = staking == null ? data.days() : data.stakingSlots();

        List<DayOutcome> outcomes = new ArrayList<>();
        for (BacktestData.PickDay day : units) {
            if (!window.contains(day.ymd())) {
                continue;
            }
            outcomes.add(runDay(algorithm, params, data, day, settings, staking));
        }
        return BacktestMetrics.from(outcomes);
    }

    private DayOutcome runDay(
            PickAlgorithm algorithm, AlgorithmParams params, BacktestData data,
            BacktestData.PickDay day, BacktestSettings settings, MartingaleStaking staking) {

        SlipSelectionInput input = new SlipSelectionInput(
                day.ymd(), settings.num(), settings.x(), settings.y(), settings.combinedN(),
                day.games(), day.priorHistory(data.history()), data.formIndex(),
                AlgorithmParams.empty()).withParams(params);

        List<SettledSlip> slips = staking == null
                ? PickBacktester.runDay(algorithm, input, day.gamesById(), settings.inputMoney())
                : PickBacktester.runStakedSlot(
                        (StakingAlgorithm) algorithm, input, day.gamesById(), staking);

        int hits = 0;
        BigDecimal inputTotal = BigDecimal.ZERO;
        BigDecimal outputTotal = BigDecimal.ZERO;
        BigDecimal benchmarkTotal = BigDecimal.ZERO;
        LegTally legs = LegTally.EMPTY;
        for (SettledSlip slip : slips) {
            if (slip.hit()) {
                hits++;
            }
            inputTotal = inputTotal.add(slip.inputMoney());
            outputTotal = outputTotal.add(slip.outputMoney());
            if (slip.benchmarkOutputMoney() != null) {
                benchmarkTotal = benchmarkTotal.add(slip.benchmarkOutputMoney());
            }
            legs = legs.plus(slip.legs());
        }
        return new DayOutcome(
                day.ymd(), slips.size(), hits, inputTotal, outputTotal, benchmarkTotal, legs);
    }
}
