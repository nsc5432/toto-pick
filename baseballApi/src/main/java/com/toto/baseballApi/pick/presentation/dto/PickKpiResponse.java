package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.toto.baseballApi.pick.application.PickKpiResult;
import com.toto.baseballApi.pick.domain.PickKpis;

public record PickKpiResponse(String groupBy, List<AlgorithmKpi> algorithms) {

    public record AlgorithmKpi(
            String algorithmCode,
            String algorithmName,
            Kpi summary,
            List<PeriodKpi> periods) {
    }

    public record Kpi(
            int slipCount,
            int hitCount,
            BigDecimal hitRate,
            BigDecimal inputTotal,
            BigDecimal outputTotal,
            BigDecimal profitRate) {
    }

    public record PeriodKpi(
            String periodKey,
            String ymd,
            Integer year,
            Integer round,
            int slipCount,
            int hitCount,
            BigDecimal hitRate,
            BigDecimal inputTotal,
            BigDecimal outputTotal,
            BigDecimal profitRate) {
    }

    public static PickKpiResponse from(PickKpiResult result) {
        List<AlgorithmKpi> algorithms = result.algorithms().stream()
                .map(algorithm -> new AlgorithmKpi(
                        algorithm.algorithmCode(),
                        algorithm.algorithmName(),
                        toKpi(algorithm.summary()),
                        algorithm.periods().stream().map(PickKpiResponse::toPeriodKpi).toList()))
                .toList();
        return new PickKpiResponse(result.groupBy().name().toLowerCase(Locale.ROOT), algorithms);
    }

    private static Kpi toKpi(PickKpiResult.Kpi kpi) {
        return new Kpi(
                kpi.slipCount(),
                kpi.hitCount(),
                PickKpis.hitRate(kpi.hitCount(), kpi.slipCount()),
                kpi.inputTotal(),
                kpi.outputTotal(),
                PickKpis.profitRate(kpi.inputTotal(), kpi.outputTotal()));
    }

    private static PeriodKpi toPeriodKpi(PickKpiResult.PeriodKpi period) {
        return new PeriodKpi(
                period.periodKey(),
                period.ymd(),
                period.year(),
                period.round(),
                period.slipCount(),
                period.hitCount(),
                PickKpis.hitRate(period.hitCount(), period.slipCount()),
                period.inputTotal(),
                period.outputTotal(),
                PickKpis.profitRate(period.inputTotal(), period.outputTotal()));
    }
}
