package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;

import com.toto.baseballApi.pick.application.ForwardSettlementResult;

/**
 * @param legExcessReturn the out-of-sample figure the whole exercise exists for — per-leg return
 *                        beyond the market's own expectation, on games that did not exist when the
 *                        parameters were frozen. {@code null} until something settles
 */
public record ForwardSettlementResponse(
        String algorithmCode,
        int settledCount,
        int stillPending,
        int hitCount,
        int legCount,
        int legHitCount,
        BigDecimal stakedTotal,
        BigDecimal returnedTotal,
        BigDecimal legExcessReturn) {

    public static ForwardSettlementResponse from(ForwardSettlementResult result) {
        return new ForwardSettlementResponse(
                result.algorithmCode(), result.settledCount(), result.stillPending(),
                result.hitCount(), result.legs().count(), result.legs().hitCount(),
                result.stakedTotal(), result.returnedTotal(), result.legExcessReturn());
    }
}
