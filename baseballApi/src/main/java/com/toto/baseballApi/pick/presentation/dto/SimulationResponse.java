package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

import com.toto.baseballApi.pick.application.SimulationResult;
import com.toto.baseballApi.pick.domain.PickKpis;

public record SimulationResponse(List<AlgorithmRun> algorithms) {

    public record AlgorithmRun(
            String algorithmCode,
            String algorithmName,
            int dayCount,
            int slipCount,
            int hitCount,
            BigDecimal hitRate,
            BigDecimal inputTotal,
            BigDecimal outputTotal,
            BigDecimal profitRate) {
    }

    public static SimulationResponse from(SimulationResult result) {
        List<AlgorithmRun> runs = result.algorithms().stream()
                .map(run -> new AlgorithmRun(
                        run.algorithmCode(),
                        run.algorithmName(),
                        run.dayCount(),
                        run.slipCount(),
                        run.hitCount(),
                        PickKpis.hitRate(run.hitCount(), run.slipCount()),
                        run.inputTotal(),
                        run.outputTotal(),
                        PickKpis.profitRate(run.inputTotal(), run.outputTotal())))
                .toList();
        return new SimulationResponse(runs);
    }
}
