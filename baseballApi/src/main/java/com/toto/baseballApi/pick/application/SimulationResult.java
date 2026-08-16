package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;

/** Per-algorithm run totals; per-period breakdowns are served by the KPI query instead. */
public record SimulationResult(List<AlgorithmRun> algorithms) {

    public record AlgorithmRun(
            String algorithmCode,
            String algorithmName,
            int dayCount,
            int slipCount,
            int hitCount,
            BigDecimal inputTotal,
            BigDecimal outputTotal) {
    }
}
