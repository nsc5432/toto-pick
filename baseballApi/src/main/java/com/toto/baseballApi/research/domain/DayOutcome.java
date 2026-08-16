package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;

/** One day's tally for one algorithm+params — the row {@link BacktestMetrics} aggregates. */
public record DayOutcome(
        String ymd,
        int slipCount,
        int hitCount,
        BigDecimal inputTotal,
        BigDecimal outputTotal) {

    /** Signed money result of the day; negative on a losing day. */
    public BigDecimal profit() {
        return outputTotal.subtract(inputTotal);
    }
}
