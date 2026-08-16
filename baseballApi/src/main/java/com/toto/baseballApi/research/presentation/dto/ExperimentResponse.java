package com.toto.baseballApi.research.presentation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.research.domain.BacktestMetrics;
import com.toto.baseballApi.research.domain.BacktestWindow;
import com.toto.baseballApi.research.domain.Experiment;
import com.toto.baseballApi.research.domain.GoalVerdict;

/**
 * One experiment as the API exposes it. Train and validation metrics are both present and both
 * labelled: a client that showed only the better of the two would be reporting the number the
 * parameters were fitted on, which is the mistake this whole pipeline is built to prevent.
 */
public record ExperimentResponse(
        String id,
        String createdAt,
        String algorithmCode,
        String algorithmName,
        Map<String, Double> params,
        String paramSignature,
        WindowResponse trainWindow,
        WindowResponse validationWindow,
        MetricsResponse trainMetrics,
        MetricsResponse validationMetrics,
        BigDecimal score,
        boolean qualified,
        boolean goalAchieved,
        List<CheckResponse> checks,
        String failureSummary,
        String inputMoney,
        long seed,
        String note) {

    public record WindowResponse(String label, String bgngYmd, String endYmd) {

        static WindowResponse from(BacktestWindow window) {
            return window == null ? null
                    : new WindowResponse(window.label(), window.bgngYmd(), window.endYmd());
        }
    }

    public record MetricsResponse(
            int dayCount,
            int bettingDayCount,
            int slipCount,
            int hitCount,
            BigDecimal inputTotal,
            BigDecimal outputTotal,
            BigDecimal profitRate,
            BigDecimal hitRate,
            BigDecimal maxDrawdown,
            int worstLosingStreak) {

        static MetricsResponse from(BacktestMetrics metrics) {
            return metrics == null ? null : new MetricsResponse(
                    metrics.dayCount(), metrics.bettingDayCount(), metrics.slipCount(),
                    metrics.hitCount(), metrics.inputTotal(), metrics.outputTotal(),
                    metrics.profitRate(), metrics.hitRate(), metrics.maxDrawdown(),
                    metrics.worstLosingStreak());
        }
    }

    public record CheckResponse(String name, String requirement, String actual, boolean passed) {
    }

    public static ExperimentResponse from(Experiment experiment) {
        GoalVerdict verdict = experiment.verdict();
        return new ExperimentResponse(
                experiment.id(),
                experiment.createdAt(),
                experiment.algorithmCode(),
                experiment.algorithmName(),
                experiment.params(),
                experiment.paramSignature(),
                WindowResponse.from(experiment.trainWindow()),
                WindowResponse.from(experiment.validationWindow()),
                MetricsResponse.from(experiment.trainMetrics()),
                MetricsResponse.from(experiment.validationMetrics()),
                experiment.score() == null ? null : experiment.score().profitRate(),
                experiment.score() != null && experiment.score().qualified(),
                verdict != null && verdict.achieved(),
                verdict == null ? List.of() : verdict.checks().stream()
                        .map(check -> new CheckResponse(
                                check.name(), check.requirement(), check.actual(), check.passed()))
                        .toList(),
                verdict == null ? "" : verdict.failureSummary(),
                experiment.inputMoney(),
                experiment.seed(),
                experiment.note());
    }
}
