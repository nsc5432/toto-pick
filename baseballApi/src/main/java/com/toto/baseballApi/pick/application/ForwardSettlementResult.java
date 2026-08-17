package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;

import com.toto.baseballApi.pick.domain.LegTally;

/**
 * What one settlement pass resolved.
 *
 * <p>{@code legs} is the figure that matters. The research goal is stated per leg because slip-level
 * numbers swing with leg grouping alone (docs/statistical-model-design.md §12), and this is the
 * out-of-sample version of exactly that measurement — the first one in this project that no amount of
 * parameter searching could have fitted.
 *
 * @param stillPending picks whose games have not appeared in the results yet
 */
public record ForwardSettlementResult(
        String algorithmCode,
        int settledCount,
        int stillPending,
        int hitCount,
        LegTally legs,
        BigDecimal stakedTotal,
        BigDecimal returnedTotal) {

    /** Per-leg excess return over the market benchmark, or {@code null} while no leg has settled. */
    public BigDecimal legExcessReturn() {
        return legs.count() == 0 ? null : legs.excessReturn();
    }
}
