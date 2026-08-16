package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
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
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickBacktester;
import com.toto.baseballApi.pick.domain.PickMaster;
import com.toto.baseballApi.pick.domain.PickMasterRepository;
import com.toto.baseballApi.pick.domain.PickUniverse;
import com.toto.baseballApi.pick.domain.SettledSlip;
import com.toto.baseballApi.pick.domain.SlipSelectionInput;
import com.toto.baseballApi.pick.domain.TeamFormIndex;

/**
 * Backtests every requested {@link PickAlgorithm} over the same ymd range (apples-to-apples):
 * regenerates the simulation user's picks per algorithm day by day (rolling win-rate window),
 * settles them against the historical results, and reports per-algorithm run totals. Per-period
 * KPIs are then served from the persisted picks by {@link PickKpiService}.
 */
@Service
public class PickSimulationService {

    static final String SIMULATION_USER_NAME = "NSC";

    private final PickMasterRepository pickMasterRepository;
    private final BaseballResultRepository baseballResultRepository;
    private final Map<String, PickAlgorithm> algorithmsByCode;

    PickSimulationService(
            PickMasterRepository pickMasterRepository,
            BaseballResultRepository baseballResultRepository,
            List<PickAlgorithm> algorithms) {
        this.pickMasterRepository = pickMasterRepository;
        this.baseballResultRepository = baseballResultRepository;
        this.algorithmsByCode = algorithms.stream().collect(Collectors.toMap(
                PickAlgorithm::code,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Duplicate PickAlgorithm code: " + a.code());
                },
                LinkedHashMap::new));
    }

    public List<AlgorithmInfo> availableAlgorithms() {
        return algorithmsByCode.values().stream()
                .map(a -> new AlgorithmInfo(a.code(), a.name()))
                .toList();
    }

    private record DayKey(Integer year, Integer round, String ymd) {
    }

    @Transactional
    public SimulationResult simulate(SimulatePicksCommand command) {
        if (command.bgngYmd().compareTo(command.endYmd()) > 0) {
            throw new IllegalArgumentException("bgngYmd must not be after endYmd");
        }
        List<PickAlgorithm> algorithms = resolveAlgorithms(command.algorithmCodes());

        // Re-running the same range replaces the previous simulation instead of stacking onto it.
        pickMasterRepository.deleteByUserNameAndAlgorithmCodesAndYmdRange(
                SIMULATION_USER_NAME,
                algorithms.stream().map(PickAlgorithm::code).toList(),
                command.bgngYmd(), command.endYmd());

        List<BaseballResult> targetGames = baseballResultRepository.findByGameTypeAndTournamentsAndYmdBetween(
                PickUniverse.THREE_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS,
                command.bgngYmd(), command.endYmd());
        List<BaseballResult> historyGames = baseballResultRepository.findByGameTypeAndTournamentsAndYmdBefore(
                PickUniverse.TWO_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS, command.endYmd());

        Map<DayKey, List<BaseballResult>> gamesByDay = targetGames.stream()
                .collect(Collectors.groupingBy(g -> new DayKey(g.year(), g.round(), g.ymd())));
        List<DayKey> dayKeys = gamesByDay.keySet().stream()
                .sorted(Comparator.comparing(DayKey::ymd)
                        .thenComparing(DayKey::year)
                        .thenComparing(DayKey::round))
                .toList();

        // Built once for the whole run — per-day rebuilds would re-sort the full history every day.
        TeamFormIndex formIndex = TeamFormIndex.build(historyGames);

        List<SimulationResult.AlgorithmRun> runs = algorithms.stream()
                .map(algorithm -> runAlgorithm(
                        algorithm, dayKeys, gamesByDay, historyGames, formIndex, command))
                .toList();
        return new SimulationResult(runs);
    }

    private List<PickAlgorithm> resolveAlgorithms(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.copyOf(algorithmsByCode.values());
        }
        return codes.stream()
                .map(code -> {
                    PickAlgorithm algorithm = algorithmsByCode.get(code);
                    if (algorithm == null) {
                        throw new IllegalArgumentException("Unknown algorithm code: " + code);
                    }
                    return algorithm;
                })
                .toList();
    }

    private SimulationResult.AlgorithmRun runAlgorithm(
            PickAlgorithm algorithm, List<DayKey> dayKeys,
            Map<DayKey, List<BaseballResult>> gamesByDay, List<BaseballResult> historyGames,
            TeamFormIndex formIndex, SimulatePicksCommand command) {
        int dayCount = 0;
        int slipCount = 0;
        int hitCount = 0;
        BigDecimal inputTotal = BigDecimal.ZERO;
        BigDecimal outputTotal = BigDecimal.ZERO;

        for (DayKey day : dayKeys) {
            DayTotals totals = simulateDay(
                    algorithm, day, gamesByDay.get(day), historyGames, formIndex, command);
            dayCount++;
            slipCount += totals.slipCount();
            hitCount += totals.hitCount();
            inputTotal = inputTotal.add(totals.inputTotal());
            outputTotal = outputTotal.add(totals.outputTotal());
        }

        return new SimulationResult.AlgorithmRun(
                algorithm.code(), algorithm.name(), dayCount, slipCount, hitCount, inputTotal, outputTotal);
    }

    private record DayTotals(int slipCount, int hitCount, BigDecimal inputTotal, BigDecimal outputTotal) {
    }

    private DayTotals simulateDay(
            PickAlgorithm algorithm, DayKey day, List<BaseballResult> dayGames,
            List<BaseballResult> historyGames, TeamFormIndex formIndex, SimulatePicksCommand command) {
        // Rolling window: only games strictly before this day feed the win-rate ranking.
        List<BaseballResult> priorGames = historyGames.stream()
                .filter(g -> g.ymd().compareTo(day.ymd()) < 0)
                .toList();

        Map<Integer, BaseballResult> dayGamesById = dayGames.stream()
                .collect(Collectors.toMap(BaseballResult::id, Function.identity()));

        SlipSelectionInput input = new SlipSelectionInput(
                day.ymd(), command.num(), command.x(), command.y(), command.combinedN(),
                dayGames, priorGames, formIndex, AlgorithmParams.empty());
        List<SettledSlip> slips = PickBacktester.runDay(
                algorithm, input, dayGamesById, command.inputMoney());

        int hits = 0;
        BigDecimal outputSum = BigDecimal.ZERO;
        for (SettledSlip slip : slips) {
            pickMasterRepository.save(new PickMaster(
                    null, day.year(), day.round(), day.ymd(),
                    SIMULATION_USER_NAME, algorithm.code(), command.inputMoney(), slip.outputMoney(),
                    slip.details()));

            if (slip.hit()) {
                hits++;
            }
            outputSum = outputSum.add(slip.outputMoney());
        }

        BigDecimal inputSum = command.inputMoney().multiply(BigDecimal.valueOf(slips.size()));
        return new DayTotals(slips.size(), hits, inputSum, outputSum);
    }
}
