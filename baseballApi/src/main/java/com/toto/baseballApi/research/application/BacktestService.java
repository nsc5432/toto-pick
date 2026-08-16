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
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickBacktester;
import com.toto.baseballApi.pick.domain.PickUniverse;
import com.toto.baseballApi.pick.domain.SettledSlip;
import com.toto.baseballApi.pick.domain.SlipSelectionInput;
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

        List<DayKey> dayKeys = gamesByDay.keySet().stream()
                .sorted(Comparator.comparing(DayKey::ymd)
                        .thenComparing(DayKey::year)
                        .thenComparing(DayKey::round))
                .toList();

        List<BacktestData.PickDay> days = new ArrayList<>(dayKeys.size());
        for (DayKey key : dayKeys) {
            List<BaseballResult> games = gamesByDay.get(key);
            days.add(new BacktestData.PickDay(
                    key.year(), key.round(), key.ymd(), games,
                    games.stream().collect(Collectors.toMap(
                            BaseballResult::id, Function.identity(), (a, b) -> a, LinkedHashMap::new)),
                    historyPrefixLength(history, key.ymd())));
        }
        return new BacktestData(days, history, TeamFormIndex.build(history));
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

        List<DayOutcome> outcomes = new ArrayList<>();
        for (BacktestData.PickDay day : data.days()) {
            if (!window.contains(day.ymd())) {
                continue;
            }
            outcomes.add(runDay(algorithm, params, data, day, settings));
        }
        return BacktestMetrics.from(outcomes);
    }

    private DayOutcome runDay(
            PickAlgorithm algorithm, AlgorithmParams params, BacktestData data,
            BacktestData.PickDay day, BacktestSettings settings) {

        SlipSelectionInput input = new SlipSelectionInput(
                day.ymd(), settings.num(), settings.x(), settings.y(), settings.combinedN(),
                day.games(), day.priorHistory(data.history()), data.formIndex(),
                AlgorithmParams.empty()).withParams(params);

        List<SettledSlip> slips = PickBacktester.runDay(
                algorithm, input, day.gamesById(), settings.inputMoney());

        int hits = 0;
        BigDecimal outputTotal = BigDecimal.ZERO;
        BigDecimal benchmarkTotal = BigDecimal.ZERO;
        LegTally legs = LegTally.EMPTY;
        for (SettledSlip slip : slips) {
            if (slip.hit()) {
                hits++;
            }
            outputTotal = outputTotal.add(slip.outputMoney());
            if (slip.benchmarkOutputMoney() != null) {
                benchmarkTotal = benchmarkTotal.add(slip.benchmarkOutputMoney());
            }
            legs = legs.plus(slip.legs());
        }
        BigDecimal inputTotal = settings.inputMoney().multiply(BigDecimal.valueOf(slips.size()));
        return new DayOutcome(
                day.ymd(), slips.size(), hits, inputTotal, outputTotal, benchmarkTotal, legs);
    }
}
