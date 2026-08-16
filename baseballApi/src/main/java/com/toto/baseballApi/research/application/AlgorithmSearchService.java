package com.toto.baseballApi.research.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.toto.baseballApi.pick.domain.AlgorithmParams;
import com.toto.baseballApi.pick.domain.ParamSpace;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.TunableAlgorithm;
import com.toto.baseballApi.research.domain.BacktestMetrics;
import com.toto.baseballApi.research.domain.BacktestSplit;
import com.toto.baseballApi.research.domain.Experiment;
import com.toto.baseballApi.research.domain.ExperimentGoal;
import com.toto.baseballApi.research.domain.ExperimentLedger;
import com.toto.baseballApi.research.domain.GoalVerdict;
import com.toto.baseballApi.research.domain.ObjectiveScore;

import java.math.BigDecimal;

/**
 * The search itself: sweep each algorithm's parameter space, pick winners on the training window,
 * confirm them on held-out days, judge them against the declared goal, and record everything.
 *
 * <p>The two-stage train-then-validate shape is the point. Scoring every candidate on the
 * validation window and taking the best would leak the held-out days into the selection — with a
 * few hundred candidates, one of them will clear any bar there by chance alone. So parameters are
 * chosen only on {@code train}, and only a handful of leaders are ever measured on
 * {@code validation}. Each survivor's train-vs-validation gap is then visible on its
 * {@link Experiment}, which is what separates a real edge from a fitted one.
 */
@Service
public class AlgorithmSearchService {

    /** A train/validation profit-rate gap wider than this is called out as likely overfitting. */
    private static final BigDecimal OVERFIT_GAP = new BigDecimal("0.15");

    private final BacktestService backtestService;
    private final ExperimentLedger ledger;
    private final ResearchProperties properties;
    private final Map<String, PickAlgorithm> algorithmsByCode;

    AlgorithmSearchService(
            BacktestService backtestService, ExperimentLedger ledger,
            ResearchProperties properties, List<PickAlgorithm> algorithms) {
        this.backtestService = backtestService;
        this.ledger = ledger;
        this.properties = properties;
        this.algorithmsByCode = algorithms.stream().collect(Collectors.toMap(
                PickAlgorithm::code,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Duplicate PickAlgorithm code: " + a.code());
                },
                LinkedHashMap::new));
    }

    /** The standing target every experiment is judged against. */
    public ExperimentGoal goal() {
        return properties.goal().toDomain();
    }

    public List<AlgorithmSpace> algorithmSpaces() {
        return algorithmsByCode.values().stream()
                .map(algorithm -> {
                    ParamSpace space = paramSpaceOf(algorithm);
                    return new AlgorithmSpace(
                            algorithm.code(), algorithm.name(),
                            algorithm instanceof TunableAlgorithm, space.specs(), space.gridSize());
                })
                .toList();
    }

    /** Fills a partially-specified command from configuration, so callers only state deviations. */
    public SearchCommand withDefaults(SearchCommand command) {
        ResearchProperties.Search search = properties.search();
        return new SearchCommand(
                command.bgngYmd(),
                command.endYmd(),
                command.trainRatio() > 0 ? command.trainRatio() : search.trainRatio(),
                command.settings() != null ? command.settings() : BacktestSettings.DEFAULT,
                command.algorithmCodes(),
                command.maxCandidatesPerAlgorithm() > 0
                        ? command.maxCandidatesPerAlgorithm() : search.maxCandidatesPerAlgorithm(),
                command.validationTopK() > 0 ? command.validationTopK() : search.validationTopK(),
                command.seed() != 0 ? command.seed() : search.seed(),
                command.goal() != null ? command.goal() : goal(),
                command.note());
    }

    public SearchReport search(SearchCommand request) {
        SearchCommand command = withDefaults(request);
        ExperimentGoal goal = command.goal();

        BacktestData data = backtestService.load(command.bgngYmd(), command.endYmd());
        List<String> ymds = data.distinctYmds();
        if (ymds.size() < 2) {
            throw new IllegalArgumentException(
                    "Range holds %d pick day(s); a train/validation split needs at least 2".formatted(ymds.size()));
        }
        BacktestSplit split = BacktestSplit.byRatio(ymds, command.trainRatio());
        int trainGuard = trainSlipGuard(goal.minSlipCount(), ymds, split);

        List<PickAlgorithm> algorithms = resolveAlgorithms(command.algorithmCodes());
        List<Experiment> experiments = new ArrayList<>();
        int candidateCount = 0;

        for (PickAlgorithm algorithm : algorithms) {
            List<AlgorithmParams> candidates = paramSpaceOf(algorithm)
                    .candidates(command.maxCandidatesPerAlgorithm(), command.seed());
            candidateCount += candidates.size();

            // Candidates are independent and nothing here writes, so the sweep parallelizes cleanly.
            List<AlgorithmParams> leaders = candidates.parallelStream()
                    .map(params -> Map.entry(params, backtestService.evaluate(
                            algorithm, params, data, split.train(), command.settings())))
                    .sorted(Comparator.comparing(
                            entry -> ObjectiveScore.of(entry.getValue(), trainGuard),
                            ObjectiveScore.BEST_FIRST))
                    .limit(command.validationTopK())
                    .map(Map.Entry::getKey)
                    .toList();

            for (AlgorithmParams params : leaders) {
                experiments.add(toExperiment(
                        algorithm, params, data, split, command, goal, trainGuard));
            }
        }

        List<Experiment> ranked = experiments.stream()
                .sorted(Comparator.comparing(Experiment::score, ObjectiveScore.BEST_FIRST))
                .toList();
        List<Experiment> bestPerAlgorithm = ranked.stream()
                .collect(Collectors.toMap(
                        Experiment::algorithmCode, Function.identity(), (first, later) -> first,
                        LinkedHashMap::new))
                .values().stream().toList();
        List<Experiment> achieved = ranked.stream()
                .filter(experiment -> experiment.verdict().achieved())
                .toList();

        ledger.appendAll(ranked);

        return new SearchReport(
                split.train(), split.validation(), goal,
                candidateCount, ranked.size(), !achieved.isEmpty(),
                ranked, bestPerAlgorithm, achieved,
                diagnose(bestPerAlgorithm, goal));
    }

    private Experiment toExperiment(
            PickAlgorithm algorithm, AlgorithmParams params, BacktestData data,
            BacktestSplit split, SearchCommand command, ExperimentGoal goal, int trainGuard) {

        BacktestMetrics train = backtestService.evaluate(
                algorithm, params, data, split.train(), command.settings());
        BacktestMetrics validation = backtestService.evaluate(
                algorithm, params, data, split.validation(), command.settings());

        AlgorithmParams recorded = mergeWithSettings(params, command.settings());
        return new Experiment(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                algorithm.code(),
                algorithm.name(),
                recorded.values(),
                recorded.signature(),
                split.train(),
                split.validation(),
                train,
                validation,
                ObjectiveScore.of(validation, goal.minSlipCount()),
                goal.evaluate(train, validation),
                command.settings().inputMoney().toPlainString(),
                command.seed(),
                command.note());
    }

    /**
     * The swept params on top of the run's fixed settings, so the ledger records the complete
     * configuration rather than only the knobs that happened to vary — a recorded run has to be
     * re-runnable without also knowing which yaml was in effect at the time.
     */
    private AlgorithmParams mergeWithSettings(AlgorithmParams params, BacktestSettings settings) {
        Map<String, Double> merged = new LinkedHashMap<>(settings.asParams().values());
        merged.putAll(params.values());
        return AlgorithmParams.of(merged);
    }

    /**
     * The goal's slip minimum applies to the validation window; the training window is longer, so
     * ranking there against the same absolute number would wave through a candidate that bets far
     * too rarely. Scaling by day count keeps the bar at the same betting *rate* on both sides.
     */
    private int trainSlipGuard(int minSlipCount, List<String> ymds, BacktestSplit split) {
        long trainDays = ymds.stream().filter(split.train()::contains).count();
        long validationDays = Math.max(1, ymds.size() - trainDays);
        return (int) Math.max(1, Math.ceil((double) minSlipCount * trainDays / validationDays));
    }

    private ParamSpace paramSpaceOf(PickAlgorithm algorithm) {
        return algorithm instanceof TunableAlgorithm tunable ? tunable.paramSpace() : ParamSpace.empty();
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

    /**
     * Turns the leaders' failures into the next thing to try. An automated loop reads this instead
     * of re-running the same sweep with a different seed, which is the failure mode that burns a
     * search budget without learning anything.
     */
    private List<String> diagnose(List<Experiment> bestPerAlgorithm, ExperimentGoal goal) {
        if (bestPerAlgorithm.isEmpty()) {
            return List.of("평가된 후보가 없습니다. 기간을 넓히거나 algorithmCodes 필터를 확인하세요.");
        }

        List<String> lines = new ArrayList<>();
        Experiment leader = bestPerAlgorithm.get(0);
        BacktestMetrics validation = leader.validationMetrics();

        if (leader.verdict().achieved()) {
            lines.add("목표 달성: %s (%s) 검증 초과수익 %s (수익률 %s), 조합 %d건. POST /api/picks/simulate 로 확정 시뮬레이션을 남기세요."
                    .formatted(leader.algorithmCode(), leader.paramSignature(),
                            validation.legExcessReturnOrZero().toPlainString(),
                            validation.profitRateOrZero().toPlainString(), validation.slipCount()));
            return lines;
        }

        lines.add("최고 후보 %s (%s) 미달: %s"
                .formatted(leader.algorithmCode(), leader.paramSignature(),
                        leader.verdict().failureSummary()));

        if (validation.slipCount() < goal.minSlipCount()) {
            lines.add("표본 부족(%d < %d). 필터를 완화하세요 — x 하향, y 상향, 또는 combinedN 축소로 후보 경기를 늘립니다."
                    .formatted(validation.slipCount(), goal.minSlipCount()));
        }

        BigDecimal gap = leader.trainMetrics().legExcessReturnOrZero()
                .subtract(validation.legExcessReturnOrZero()).abs();
        if (gap.compareTo(OVERFIT_GAP) > 0) {
            lines.add("학습·검증 불일치: 초과수익 학습 %s vs 검증 %s (격차 %s). 한쪽 구간이 특이했다는 뜻이므로, 격자를 좁히거나 기간을 늘려 재검증하세요."
                    .formatted(leader.trainMetrics().legExcessReturnOrZero().toPlainString(),
                            validation.legExcessReturnOrZero().toPlainString(), gap.toPlainString()));
        }

        // A promising-looking edge on too few slips is not evidence the family works, so an
        // unqualified candidate must not count toward "something here has an edge" — otherwise
        // one lucky run suppresses the advice to go find a genuinely different signal.
        bestPerAlgorithm.stream()
                .filter(e -> !e.score().qualified()
                        && e.validationMetrics().legExcessReturnOrZero().compareTo(BigDecimal.ZERO) > 0)
                .forEach(e -> lines.add(
                        "%s (%s) 는 검증 초과수익 %s 이나 조합 %d건(< %d)으로 실격입니다. 적중 %d건에 기댄 수치이므로 그대로 신뢰하지 말고, 기간을 늘리거나 필터를 완화해 표본을 확보한 뒤 재검증하세요."
                                .formatted(e.algorithmCode(), e.paramSignature(),
                                        e.validationMetrics().legExcessReturnOrZero().toPlainString(),
                                        e.validationMetrics().slipCount(), goal.minSlipCount(),
                                        e.validationMetrics().hitCount())));

        boolean noQualifiedEdge = bestPerAlgorithm.stream().noneMatch(e -> e.score().qualified()
                && e.validationMetrics().legExcessReturnOrZero().compareTo(BigDecimal.ZERO) > 0);
        if (noQualifiedEdge) {
            lines.add("표본 기준을 통과한 후보 중 검증 구간 초과수익이 양(+)인 계열이 없습니다 — 어느 계열도 배당이 모르는 정보를 담고 있지 않다는 뜻입니다. 기존 계열의 파라미터 재탐색보다 새로운 신호 계열(도출 방법)을 추가하는 편이 낫습니다.");
        }

        return lines;
    }

    /** One-off backtest of a single configuration — the "결과확인" step without running a sweep. */
    public Experiment backtestOne(
            String algorithmCode, AlgorithmParams params, SearchCommand request) {
        SearchCommand command = withDefaults(request);
        PickAlgorithm algorithm = resolveAlgorithms(List.of(algorithmCode)).get(0);

        BacktestData data = backtestService.load(command.bgngYmd(), command.endYmd());
        List<String> ymds = data.distinctYmds();
        if (ymds.size() < 2) {
            throw new IllegalArgumentException("Range holds too few pick days to split");
        }
        BacktestSplit split = BacktestSplit.byRatio(ymds, command.trainRatio());

        Experiment experiment = toExperiment(
                algorithm, params, data, split, command, command.goal(),
                trainSlipGuard(command.goal().minSlipCount(), ymds, split));
        ledger.appendAll(List.of(experiment));
        return experiment;
    }

    /** Best recorded experiment per algorithm across every sweep ever run. */
    public List<Experiment> leaderboard(int limit) {
        return ledger.leaderboard(limit);
    }

    public List<Experiment> history() {
        return ledger.findAll();
    }

    /** Whether anything in the ledger has ever cleared the current goal. */
    public GoalVerdict bestVerdict() {
        return ledger.leaderboard(1).stream()
                .findFirst()
                .map(Experiment::verdict)
                .orElse(new GoalVerdict(false, List.of()));
    }
}
