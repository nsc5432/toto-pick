package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.toto.baseballApi.pick.application.SimulationResult;

public record SimulationResponse(Summary summary, List<Day> days) {

    public record Summary(
            int dayCount,
            int slipCount,
            int hitCount,
            BigDecimal inputTotal,
            BigDecimal outputTotal,
            BigDecimal profitRate) {
    }

    public record Day(
            String ymd,
            Integer year,
            Integer round,
            int slipCount,
            int hitCount,
            BigDecimal inputTotal,
            BigDecimal outputTotal,
            BigDecimal profitRate) {
    }

    public static SimulationResponse from(SimulationResult result) {
        Summary summary = new Summary(
                result.dayCount(),
                result.slipCount(),
                result.hitCount(),
                result.inputTotal(),
                result.outputTotal(),
                profitRate(result.inputTotal(), result.outputTotal()));

        List<Day> days = result.days().stream()
                .map(day -> new Day(
                        day.ymd(),
                        day.year(),
                        day.round(),
                        day.slipCount(),
                        day.hitCount(),
                        day.inputTotal(),
                        day.outputTotal(),
                        profitRate(day.inputTotal(), day.outputTotal())))
                .toList();

        return new SimulationResponse(summary, days);
    }

    private static BigDecimal profitRate(BigDecimal input, BigDecimal output) {
        if (input.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return output.subtract(input).divide(input, 4, RoundingMode.HALF_UP);
    }
}
