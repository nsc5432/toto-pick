package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;

import com.toto.baseballApi.pick.domain.LegTally;

/**
 * One day's tally for one algorithm+params — the row {@link BacktestMetrics} aggregates.
 *
 * @param benchmarkOutputTotal what the day's slips were worth under the market's own probabilities
 *                             (see {@code PickSettlement.marketExpectation}); the yardstick the
 *                             day's actual {@code outputTotal} is judged against
 */
public record DayOutcome(
        String ymd,
        int slipCount,
        int hitCount,
        BigDecimal inputTotal,
        BigDecimal outputTotal,
        BigDecimal benchmarkOutputTotal,
        LegTally legs) {

    public DayOutcome {
        legs = legs == null ? LegTally.EMPTY : legs;
    }

    /** Day tally without a benchmark — for tests and callers that only measure raw profit. */
    public DayOutcome(
            String ymd, int slipCount, int hitCount, BigDecimal inputTotal, BigDecimal outputTotal) {
        this(ymd, slipCount, hitCount, inputTotal, outputTotal, null, LegTally.EMPTY);
    }

    /** Day tally with the slip-level benchmark but no leg breakdown — for tests. */
    public DayOutcome(
            String ymd, int slipCount, int hitCount, BigDecimal inputTotal, BigDecimal outputTotal,
            BigDecimal benchmarkOutputTotal) {
        this(ymd, slipCount, hitCount, inputTotal, outputTotal, benchmarkOutputTotal, LegTally.EMPTY);
    }

    /** Signed money result of the day; negative on a losing day. */
    public BigDecimal profit() {
        return outputTotal.subtract(inputTotal);
    }
}
