package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The declared target a candidate has to clear to count as "found" — the pipeline's stopping
 * condition, written down before the search runs rather than after the results are in.
 *
 * <p>Stating it up front is what stops the target from drifting to meet whatever the sweep happened
 * to produce. {@code targetProfitRate} and the sample gates are always checked;
 * {@code minHitRate} and {@code maxDrawdown} are optional and skipped when null.
 *
 * <p>Always evaluate this against {@link BacktestWindow#VALIDATION} metrics. Train-window numbers
 * are the numbers the parameters were selected on, so clearing the target there means nothing.
 */
public record ExperimentGoal(
        BigDecimal targetProfitRate,
        int minSlipCount,
        int minBettingDayCount,
        BigDecimal minHitRate,
        BigDecimal maxDrawdown) {

    public ExperimentGoal {
        if (targetProfitRate == null) {
            throw new IllegalArgumentException("targetProfitRate must not be null");
        }
        if (minSlipCount < 1) {
            throw new IllegalArgumentException("minSlipCount must be >= 1 — an unguarded target is meaningless");
        }
        if (minBettingDayCount < 1) {
            throw new IllegalArgumentException("minBettingDayCount must be >= 1");
        }
    }

    public GoalVerdict evaluate(BacktestMetrics metrics) {
        List<GoalCheck> checks = new ArrayList<>();

        checks.add(new GoalCheck(
                "수익률",
                ">= " + targetProfitRate.toPlainString(),
                metrics.profitRate() == null ? "표본 없음" : metrics.profitRate().toPlainString(),
                metrics.profitRate() != null && metrics.profitRate().compareTo(targetProfitRate) >= 0));

        checks.add(new GoalCheck(
                "최소 조합 수",
                ">= " + minSlipCount,
                String.valueOf(metrics.slipCount()),
                metrics.slipCount() >= minSlipCount));

        checks.add(new GoalCheck(
                "최소 베팅일 수",
                ">= " + minBettingDayCount,
                String.valueOf(metrics.bettingDayCount()),
                metrics.bettingDayCount() >= minBettingDayCount));

        if (minHitRate != null) {
            checks.add(new GoalCheck(
                    "최소 적중률",
                    ">= " + minHitRate.toPlainString(),
                    metrics.hitRate() == null ? "표본 없음" : metrics.hitRate().toPlainString(),
                    metrics.hitRate() != null && metrics.hitRate().compareTo(minHitRate) >= 0));
        }

        if (maxDrawdown != null) {
            checks.add(new GoalCheck(
                    "최대 낙폭 한도",
                    "<= " + maxDrawdown.toPlainString(),
                    metrics.maxDrawdown().toPlainString(),
                    metrics.maxDrawdown().compareTo(maxDrawdown) <= 0));
        }

        return new GoalVerdict(checks.stream().allMatch(GoalCheck::passed), checks);
    }
}
