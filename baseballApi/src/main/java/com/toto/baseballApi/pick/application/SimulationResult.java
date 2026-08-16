package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;

public record SimulationResult(
        List<DayResult> days,
        int dayCount,
        int slipCount,
        int hitCount,
        BigDecimal inputTotal,
        BigDecimal outputTotal) {

    public record DayResult(
            String ymd,
            Integer year,
            Integer round,
            int slipCount,
            int hitCount,
            BigDecimal inputTotal,
            BigDecimal outputTotal) {
    }
}
