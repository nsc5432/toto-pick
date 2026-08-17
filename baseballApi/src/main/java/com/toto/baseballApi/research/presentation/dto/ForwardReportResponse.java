package com.toto.baseballApi.research.presentation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.pick.domain.LegTally;
import com.toto.baseballApi.research.domain.ForwardReport;
import com.toto.baseballApi.research.domain.GoalVerdict;
import com.toto.baseballApi.research.presentation.dto.ExperimentResponse.CheckResponse;
import com.toto.baseballApi.research.presentation.dto.ExperimentResponse.MetricsResponse;

/**
 * One frozen candidate's forward-test standing.
 *
 * <p>Reuses {@link MetricsResponse} and {@link CheckResponse} so a forward figure and a backtest
 * figure appear under the same names on the same scales — "the backtest said X, the forward test says
 * Y" is the entire point, and a bespoke shape here would obscure it.
 */
public record ForwardReportResponse(
        String algorithmCode,
        String algorithmName,
        String userName,
        List<String> paramSignatures,
        int slipCount,
        int settledSlipCount,
        int pendingSlipCount,
        List<String> pendingYmds,
        MetricsResponse metrics,
        boolean goalAchieved,
        List<CheckResponse> checks,
        String failureSummary,
        BaselineResponse awayBaseline,
        BigDecimal excessOverAwayBaseline,
        Map<String, Integer> backedSideCounts,
        CoverageResponse coverage) {

    /** The away-backing control, measured on the same legs. */
    public record BaselineResponse(
            int legCount,
            int legHitCount,
            BigDecimal legHitRate,
            BigDecimal legReturnRate,
            BigDecimal legBenchmarkRate,
            BigDecimal legExcessReturn,
            BigDecimal legExcessTStat) {

        static BaselineResponse from(LegTally legs) {
            if (legs == null || legs.count() == 0) {
                return null;
            }
            return new BaselineResponse(
                    legs.count(), legs.hitCount(),
                    BigDecimal.valueOf(legs.hitCount())
                            .divide(BigDecimal.valueOf(legs.count()), 4, RoundingMode.HALF_UP),
                    legs.returnRate(), legs.benchmarkRate(),
                    legs.excessReturn(), legs.excessTStat());
        }
    }

    /** Whether the daily loop actually ran; {@code missedYmds} non-empty means it did not. */
    public record CoverageResponse(
            String firstYmd,
            String lastYmd,
            int playedDays,
            int coveredDays,
            List<String> missedYmds) {

        static CoverageResponse from(ForwardReport.Coverage coverage) {
            return new CoverageResponse(
                    coverage.firstYmd(), coverage.lastYmd(),
                    coverage.playedDays(), coverage.coveredDays(), coverage.missedYmds());
        }
    }

    public static ForwardReportResponse from(ForwardReport report) {
        GoalVerdict verdict = report.verdict();
        return new ForwardReportResponse(
                report.algorithmCode(),
                report.algorithmName(),
                report.userName(),
                report.paramSignatures(),
                report.slipCount(),
                report.settledSlipCount(),
                report.pendingSlipCount(),
                report.pendingYmds(),
                MetricsResponse.from(report.metrics()),
                verdict != null && verdict.achieved(),
                verdict == null ? List.of() : verdict.checks().stream()
                        .map(check -> new CheckResponse(
                                check.name(), check.requirement(), check.actual(), check.passed()))
                        .toList(),
                verdict == null ? "" : verdict.failureSummary(),
                BaselineResponse.from(report.awayBaseline()),
                report.excessOverAwayBaseline(),
                report.backedSideCounts(),
                CoverageResponse.from(report.coverage()));
    }
}
