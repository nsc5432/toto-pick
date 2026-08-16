package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;
import java.util.List;

import com.toto.baseballApi.pick.domain.LegTally;
import com.toto.baseballApi.pick.domain.PickKpis;

/**
 * What one algorithm+params scored over one {@link BacktestWindow}.
 *
 * <p>{@code profitRate} and {@code hitRate} reuse {@link PickKpis} so a research number and the KPI
 * dashboard's number for the same run are the same number. The three risk fields exist because ROI
 * alone hides how a run got there: two candidates can both end at +12% while one bled through a
 * 30-day losing stretch that no one would actually sit through.
 *
 * @param maxDrawdown       largest peak-to-trough fall of cumulative profit, as a positive amount
 *                          (zero when the equity curve never fell back)
 * @param worstLosingStreak longest run of consecutive betting days that finished under water
 * @param bettingDayCount   days the algorithm actually placed a slip on; days it stood aside are
 *                          excluded, since standing aside is not a losing day
 * @param segmentCount      how many equal chronological slices of the betting days were measured —
 *                          {@link #SEGMENTS}, or fewer when there are not that many betting days
 * @param profitableSegmentCount how many of those slices finished in profit. A window's total ROI
 *                          says nothing about how it got there: an edge earns across the window,
 *                          while a lucky tail earns in one slice and loses in the rest. §10 of
 *                          docs/statistical-model-design.md is exactly that case — +33% overall,
 *                          profitable in 2 slices of 4
 * @param benchmarkOutputTotal what the same slips were worth under the market's own probabilities
 *                          (see {@code PickSettlement.marketExpectation})
 * @param excessReturn      {@code (outputTotal − benchmarkOutputTotal) / inputTotal} — the slip-level
 *                          edge, i.e. what the product actually delivered above the market's own
 *                          expectation. Reported, but far too noisy to judge on: two runs backing
 *                          identical games differ by tens of points here purely by leg grouping
 * @param legs              the same window taken leg by leg. {@link LegTally#excessReturn()} is what
 *                          the goal is stated against — {@code profitRate} answers "did it earn?",
 *                          which in a 15%-margin market is nearly always no and says nothing about
 *                          whether the strategy knew anything
 */
public record BacktestMetrics(
        int dayCount,
        int bettingDayCount,
        int slipCount,
        int hitCount,
        BigDecimal inputTotal,
        BigDecimal outputTotal,
        BigDecimal profitRate,
        BigDecimal hitRate,
        BigDecimal maxDrawdown,
        int worstLosingStreak,
        int segmentCount,
        int profitableSegmentCount,
        BigDecimal benchmarkOutputTotal,
        BigDecimal excessReturn,
        LegTally legs) {

    public BacktestMetrics {
        legs = legs == null ? LegTally.EMPTY : legs;
    }

    /** Quarters: fine enough to expose a one-tail run, coarse enough that each slice holds slips. */
    public static final int SEGMENTS = 4;

    public static BacktestMetrics from(List<DayOutcome> outcomes) {
        int bettingDayCount = 0;
        int slipCount = 0;
        int hitCount = 0;
        BigDecimal inputTotal = BigDecimal.ZERO;
        BigDecimal outputTotal = BigDecimal.ZERO;
        BigDecimal benchmarkTotal = BigDecimal.ZERO;
        boolean benchmarked = false;

        LegTally legs = LegTally.EMPTY;

        BigDecimal cumulativeProfit = BigDecimal.ZERO;
        BigDecimal peakProfit = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        int losingStreak = 0;
        int worstLosingStreak = 0;

        for (DayOutcome outcome : outcomes) {
            slipCount += outcome.slipCount();
            hitCount += outcome.hitCount();
            inputTotal = inputTotal.add(outcome.inputTotal());
            outputTotal = outputTotal.add(outcome.outputTotal());
            if (outcome.benchmarkOutputTotal() != null) {
                benchmarkTotal = benchmarkTotal.add(outcome.benchmarkOutputTotal());
                benchmarked = true;
            }
            legs = legs.plus(outcome.legs());

            if (outcome.slipCount() == 0) {
                // Standing aside is neither a losing day nor a break in an ongoing losing run.
                continue;
            }
            bettingDayCount++;

            cumulativeProfit = cumulativeProfit.add(outcome.profit());
            if (cumulativeProfit.compareTo(peakProfit) > 0) {
                peakProfit = cumulativeProfit;
            }
            BigDecimal drawdown = peakProfit.subtract(cumulativeProfit);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }

            if (outcome.profit().compareTo(BigDecimal.ZERO) < 0) {
                losingStreak++;
                worstLosingStreak = Math.max(worstLosingStreak, losingStreak);
            } else {
                losingStreak = 0;
            }
        }

        List<DayOutcome> bettingDays = outcomes.stream()
                .filter(outcome -> outcome.slipCount() > 0)
                .toList();
        int segmentCount = Math.min(SEGMENTS, bettingDays.size());

        BigDecimal benchmarkOutputTotal = benchmarked ? benchmarkTotal : null;
        return new BacktestMetrics(
                outcomes.size(), bettingDayCount, slipCount, hitCount, inputTotal, outputTotal,
                PickKpis.profitRate(inputTotal, outputTotal), PickKpis.hitRate(hitCount, slipCount),
                maxDrawdown, worstLosingStreak,
                segmentCount, profitableSegmentCount(bettingDays, segmentCount),
                benchmarkOutputTotal, excessReturn(inputTotal, outputTotal, benchmarkOutputTotal),
                legs);
    }

    /** Leg-level edge — the number the goal is judged on. See {@link LegTally}. */
    public BigDecimal legExcessReturn() {
        return legs.excessReturn();
    }

    /** {@link #legExcessReturn()} with nulls flattened to zero, for comparisons and ranking. */
    public BigDecimal legExcessReturnOrZero() {
        BigDecimal excess = legs.excessReturn();
        return excess == null ? BigDecimal.ZERO : excess;
    }

    /**
     * {@code (output − benchmark) / input}, or {@code null} when nothing was bet or no benchmark
     * could be quoted — an absent number, not a zero one, since zero here means "no edge" and would
     * be a claim the data does not support.
     */
    private static BigDecimal excessReturn(
            BigDecimal inputTotal, BigDecimal outputTotal, BigDecimal benchmarkOutputTotal) {
        if (benchmarkOutputTotal == null || inputTotal.signum() == 0) {
            return null;
        }
        return outputTotal.subtract(benchmarkOutputTotal)
                .divide(inputTotal, 4, java.math.RoundingMode.HALF_UP);
    }

    /** {@code excessReturn} with nulls flattened to zero, for comparisons that cannot carry a null. */
    public BigDecimal excessReturnOrZero() {
        return excessReturn == null ? BigDecimal.ZERO : excessReturn;
    }

    /**
     * Splits the betting days into {@code segmentCount} contiguous, near-equal slices and counts the
     * ones that finished in profit. Slices are cut on betting days rather than calendar days so a
     * quiet stretch cannot become an empty slice that scores as a loss.
     */
    private static int profitableSegmentCount(List<DayOutcome> bettingDays, int segmentCount) {
        int profitable = 0;
        for (int segment = 0; segment < segmentCount; segment++) {
            int from = (int) ((long) bettingDays.size() * segment / segmentCount);
            int to = (int) ((long) bettingDays.size() * (segment + 1) / segmentCount);
            BigDecimal profit = BigDecimal.ZERO;
            for (DayOutcome outcome : bettingDays.subList(from, to)) {
                profit = profit.add(outcome.profit());
            }
            if (profit.compareTo(BigDecimal.ZERO) > 0) {
                profitable++;
            }
        }
        return profitable;
    }

    /** {@code profitRate} with nulls flattened to zero, for comparisons that cannot carry a null. */
    public BigDecimal profitRateOrZero() {
        return profitRate == null ? BigDecimal.ZERO : profitRate;
    }
}
