package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;

/**
 * Lightweight read model for KPI aggregation: one slip's money outcome plus the grouping keys
 * (algorithm, day, round). Deliberately excludes details — KPI queries never need them.
 */
public record PickKpiRow(
        String algorithmCode,
        String ymd,
        Integer year,
        Integer round,
        BigDecimal inputMoney,
        BigDecimal outputMoney) {
}
