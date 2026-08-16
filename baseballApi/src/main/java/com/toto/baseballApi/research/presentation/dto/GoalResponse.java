package com.toto.baseballApi.research.presentation.dto;

import java.math.BigDecimal;

import com.toto.baseballApi.research.domain.ExperimentGoal;

/** The standing target, so a client can show what "done" means without hardcoding it. */
public record GoalResponse(
        BigDecimal targetProfitRate,
        int minSlipCount,
        int minBettingDayCount,
        BigDecimal minHitRate,
        BigDecimal maxDrawdown,
        BigDecimal maxTrainValidationGap,
        Integer minProfitableSegments,
        BigDecimal minExcessReturn,
        BigDecimal minExcessTStat,
        BigDecimal maxTrainValidationGapTStat) {

    public static GoalResponse from(ExperimentGoal goal) {
        return new GoalResponse(
                goal.targetProfitRate(), goal.minSlipCount(), goal.minBettingDayCount(),
                goal.minHitRate(), goal.maxDrawdown(),
                goal.maxTrainValidationGap(), goal.minProfitableSegments(),
                goal.minExcessReturn(), goal.minExcessTStat(), goal.maxTrainValidationGapTStat());
    }
}
