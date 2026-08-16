package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.pick.domain.PickDetail;
import com.toto.baseballApi.pick.domain.PickMaster;
import com.toto.baseballApi.pick.domain.PickMasterRepository;
import com.toto.baseballApi.pick.domain.PickSettlement;
import com.toto.baseballApi.pick.domain.PickSlip;
import com.toto.baseballApi.pick.domain.SlipSelectionInput;
import com.toto.baseballApi.pick.domain.WinRateOddsSlipSelector;

import lombok.RequiredArgsConstructor;

/**
 * Backtests the {@link WinRateOddsSlipSelector} strategy over a ymd range: regenerates the
 * simulation user's picks day by day (rolling win-rate window), settles them against the
 * historical results, and reports per-day KPIs.
 */
@Service
@RequiredArgsConstructor
public class PickSimulationService {

    static final String SIMULATION_USER_NAME = "NSC";
    private static final List<String> TOURNAMENTS = List.of("KBO", "NPB", "MLB");
    private static final String THREE_WAY_GAME_TYPE = "야구 승1패";
    private static final String TWO_WAY_GAME_TYPE = "야구 승패";

    private final PickMasterRepository pickMasterRepository;
    private final BaseballResultRepository baseballResultRepository;
    private final WinRateOddsSlipSelector selector = new WinRateOddsSlipSelector();

    private record DayKey(Integer year, Integer round, String ymd) {
    }

    @Transactional
    public SimulationResult simulate(SimulatePicksCommand command) {
        if (command.bgngYmd().compareTo(command.endYmd()) > 0) {
            throw new IllegalArgumentException("bgngYmd must not be after endYmd");
        }

        // Re-running the same range replaces the previous simulation instead of stacking onto it.
        pickMasterRepository.deleteByUserNameAndYmdRange(
                SIMULATION_USER_NAME, command.bgngYmd(), command.endYmd());

        List<BaseballResult> targetGames = baseballResultRepository.findByGameTypeAndTournamentsAndYmdBetween(
                THREE_WAY_GAME_TYPE, TOURNAMENTS, command.bgngYmd(), command.endYmd());
        List<BaseballResult> historyGames = baseballResultRepository.findByGameTypeAndTournamentsAndYmdBefore(
                TWO_WAY_GAME_TYPE, TOURNAMENTS, command.endYmd());

        Map<DayKey, List<BaseballResult>> gamesByDay = targetGames.stream()
                .collect(Collectors.groupingBy(g -> new DayKey(g.year(), g.round(), g.ymd())));
        List<DayKey> dayKeys = gamesByDay.keySet().stream()
                .sorted(Comparator.comparing(DayKey::ymd)
                        .thenComparing(DayKey::year)
                        .thenComparing(DayKey::round))
                .toList();

        List<SimulationResult.DayResult> days = new ArrayList<>();
        int slipCount = 0;
        int hitCount = 0;
        BigDecimal inputTotal = BigDecimal.ZERO;
        BigDecimal outputTotal = BigDecimal.ZERO;

        for (DayKey day : dayKeys) {
            SimulationResult.DayResult dayResult =
                    simulateDay(day, gamesByDay.get(day), historyGames, command);
            days.add(dayResult);
            slipCount += dayResult.slipCount();
            hitCount += dayResult.hitCount();
            inputTotal = inputTotal.add(dayResult.inputTotal());
            outputTotal = outputTotal.add(dayResult.outputTotal());
        }

        return new SimulationResult(days, days.size(), slipCount, hitCount, inputTotal, outputTotal);
    }

    private SimulationResult.DayResult simulateDay(
            DayKey day, List<BaseballResult> dayGames, List<BaseballResult> historyGames,
            SimulatePicksCommand command) {
        // Rolling window: only games strictly before this day feed the win-rate ranking.
        List<BaseballResult> priorGames = historyGames.stream()
                .filter(g -> g.ymd().compareTo(day.ymd()) < 0)
                .toList();

        List<PickSlip> slips = selector.selectSlips(new SlipSelectionInput(
                day.ymd(), command.num(), command.x(), command.y(), command.combinedN(),
                dayGames, priorGames));

        Map<Integer, BaseballResult> dayGamesById = dayGames.stream()
                .collect(Collectors.toMap(BaseballResult::id, Function.identity()));

        int hits = 0;
        BigDecimal outputSum = BigDecimal.ZERO;
        for (PickSlip slip : slips) {
            List<PickDetail> details = slip.selections().stream()
                    .map(s -> new PickDetail(null, s.resultId(), s.predictedTotalResult()))
                    .toList();
            BigDecimal outputMoney = PickSettlement.settle(details, dayGamesById, command.inputMoney());

            pickMasterRepository.save(new PickMaster(
                    null, day.year(), day.round(), day.ymd(),
                    SIMULATION_USER_NAME, command.inputMoney(), outputMoney, details));

            if (outputMoney.compareTo(BigDecimal.ZERO) > 0) {
                hits++;
            }
            outputSum = outputSum.add(outputMoney);
        }

        BigDecimal inputSum = command.inputMoney().multiply(BigDecimal.valueOf(slips.size()));
        return new SimulationResult.DayResult(
                day.ymd(), day.year(), day.round(), slips.size(), hits, inputSum, outputSum);
    }
}
