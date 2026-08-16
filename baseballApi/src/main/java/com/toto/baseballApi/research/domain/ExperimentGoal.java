package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The declared target a candidate has to clear to count as "found" — the pipeline's stopping
 * condition, written down before the search runs rather than after the results are in.
 *
 * <p>Stating it up front is what stops the target from drifting to meet whatever the sweep happened
 * to produce. The sample gates are always checked; every performance bar —
 * {@code minExcessReturn}, {@code targetProfitRate}, {@code minHitRate}, {@code maxDrawdown},
 * {@code maxTrainValidationGap}, {@code minProfitableSegments} — is optional and skipped when null,
 * but at least one of the first two must be declared or the goal asserts nothing about results.
 *
 * <p>The target itself is always read off {@link BacktestWindow#VALIDATION} metrics. Train-window
 * numbers are the numbers the parameters were selected on, so clearing the target there means
 * nothing — the train window enters only through {@code maxTrainValidationGap}, as a consistency
 * check, never as something a candidate can pass by scoring well on.
 *
 * @param maxTrainValidationGapTStat ceiling on how many standard errors separate the two windows'
 *                              edges. A window total can be carried by one lucky stretch, and the
 *                              tell is that the two windows disagree by more than sampling noise
 *                              explains — a real edge shows up in both. Stated in σ for the same
 *                              reason the target is: an absolute gap is sample-size-blind, and the
 *                              same 0.17 that is 2σ for one candidate is 2.2σ for another
 * @param maxTrainValidationGap optional absolute ceiling on {@code |train − validation|} excess,
 *                              kept as a blunt secondary bound
 * @param minProfitableSegments floor on {@link BacktestMetrics#profitableSegmentCount()}, i.e. how
 *                              many quarters of the validation window must finish in profit
 * @param minExcessReturn   floor on {@link BacktestMetrics#excessReturn()} — how far the strategy's
 *                          picks must beat what the same stakes were worth under the market's own
 *                          probabilities. This asks whether the strategy knew something, which is a
 *                          question this data can answer; {@code targetProfitRate} asks whether it
 *                          also out-earned a 15.3% house margin, which measurement says nothing here
 *                          does (docs/statistical-model-design.md §7–§11). Separating the two is the
 *                          point: an edge that does not yet cover the margin is still a finding, and
 *                          the only route to one that does
 * @param minExcessTStat    how many standard errors that edge must sit above zero. The excess
 *                          floor alone is not a bar: on 300 legs the noise is worth about 7%p by
 *                          itself, so a +5%p reading there is unremarkable while the same reading on
 *                          900 legs is not. Requiring both means a strategy can earn its claim
 *                          either by showing a large edge or by betting often enough to prove a
 *                          small one
 * @param targetProfitRate  optional absolute ROI floor, kept so a profit target can be reinstated
 *                          without a code change
 */
public record ExperimentGoal(
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

    public ExperimentGoal {
        if (targetProfitRate == null && minExcessReturn == null) {
            throw new IllegalArgumentException(
                    "Declare targetProfitRate or minExcessReturn — a goal with neither judges nothing");
        }
        if (minSlipCount < 1) {
            throw new IllegalArgumentException("minSlipCount must be >= 1 — an unguarded target is meaningless");
        }
        if (minBettingDayCount < 1) {
            throw new IllegalArgumentException("minBettingDayCount must be >= 1");
        }
        if (maxTrainValidationGap != null && maxTrainValidationGap.signum() < 0) {
            throw new IllegalArgumentException("maxTrainValidationGap must not be negative");
        }
    }

    /**
     * Judges the validation window alone. Use {@link #evaluate(BacktestMetrics, BacktestMetrics)}
     * wherever a train window exists — the stability check is skipped here.
     */
    public GoalVerdict evaluate(BacktestMetrics metrics) {
        return evaluate(null, metrics);
    }

    public GoalVerdict evaluate(BacktestMetrics train, BacktestMetrics metrics) {
        List<GoalCheck> checks = new ArrayList<>();

        if (minExcessReturn != null) {
            checks.add(new GoalCheck(
                    "시장 대비 초과수익",
                    ">= " + minExcessReturn.toPlainString(),
                    metrics.legExcessReturn() == null ? "표본 없음" : metrics.legExcessReturn().toPlainString(),
                    metrics.legExcessReturn() != null
                            && metrics.legExcessReturn().compareTo(minExcessReturn) >= 0));
        }

        if (minExcessTStat != null) {
            BigDecimal tStat = metrics.legs().excessTStat();
            checks.add(new GoalCheck(
                    "초과수익 유의성(t)",
                    ">= " + minExcessTStat.toPlainString(),
                    tStat == null ? "표본 없음" : tStat.toPlainString(),
                    tStat != null && tStat.compareTo(minExcessTStat) >= 0));
        }

        if (targetProfitRate != null) {
            checks.add(new GoalCheck(
                    "수익률",
                    ">= " + targetProfitRate.toPlainString(),
                    metrics.profitRate() == null ? "표본 없음" : metrics.profitRate().toPlainString(),
                    metrics.profitRate() != null
                            && metrics.profitRate().compareTo(targetProfitRate) >= 0));
        }

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

        if (minProfitableSegments != null) {
            checks.add(new GoalCheck(
                    "수익 구간 수",
                    ">= " + minProfitableSegments + " / " + BacktestMetrics.SEGMENTS,
                    metrics.profitableSegmentCount() + " / " + metrics.segmentCount(),
                    metrics.profitableSegmentCount() >= minProfitableSegments));
        }

        if (train != null) {
            BigDecimal gap = train.legExcessReturnOrZero()
                    .subtract(metrics.legExcessReturnOrZero()).abs();

            if (maxTrainValidationGapTStat != null) {
                BigDecimal gapTStat = gapTStat(train, metrics, gap);
                checks.add(new GoalCheck(
                        "학습·검증 불일치(σ)",
                        "<= " + maxTrainValidationGapTStat.toPlainString(),
                        gapTStat == null ? "표본 없음" : gapTStat.toPlainString(),
                        gapTStat != null && gapTStat.compareTo(maxTrainValidationGapTStat) <= 0));
            }

            if (maxTrainValidationGap != null) {
                checks.add(new GoalCheck(
                        "학습·검증 격차",
                        "<= " + maxTrainValidationGap.toPlainString(),
                        gap.toPlainString(),
                        gap.compareTo(maxTrainValidationGap) <= 0));
            }
        }

        return new GoalVerdict(checks.stream().allMatch(GoalCheck::passed), checks);
    }

    /** The two windows' disagreement in standard errors of the difference. */
    private static BigDecimal gapTStat(
            BacktestMetrics train, BacktestMetrics validation, BigDecimal gap) {
        Double trainError = train.legs().standardError();
        Double validationError = validation.legs().standardError();
        if (trainError == null || validationError == null) {
            return null;
        }
        double combined = Math.sqrt(trainError * trainError + validationError * validationError);
        return combined > 0
                ? BigDecimal.valueOf(gap.doubleValue() / combined).setScale(2, java.math.RoundingMode.HALF_UP)
                : null;
    }
}
