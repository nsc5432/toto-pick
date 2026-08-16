package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;

public record PickKpiResult(PickKpiQuery.GroupBy groupBy, List<AlgorithmKpi> algorithms) {

    public record AlgorithmKpi(
            String algorithmCode,
            String algorithmName,
            Kpi summary,
            List<PeriodKpi> periods) {
    }

    public record Kpi(
            int slipCount,
            int hitCount,
            BigDecimal inputTotal,
            BigDecimal outputTotal) {
    }

    /** {@code ymd} is null when grouped by round; {@code periodKey} is ymd or "year-round". */
    public record PeriodKpi(
            String periodKey,
            String ymd,
            Integer year,
            Integer round,
            int slipCount,
            int hitCount,
            BigDecimal inputTotal,
            BigDecimal outputTotal) {
    }
}
