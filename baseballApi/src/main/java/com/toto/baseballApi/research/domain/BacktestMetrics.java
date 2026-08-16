package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;
import java.util.List;

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
        int worstLosingStreak) {

    public static BacktestMetrics from(List<DayOutcome> outcomes) {
        int bettingDayCount = 0;
        int slipCount = 0;
        int hitCount = 0;
        BigDecimal inputTotal = BigDecimal.ZERO;
        BigDecimal outputTotal = BigDecimal.ZERO;

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

        return new BacktestMetrics(
                outcomes.size(), bettingDayCount, slipCount, hitCount, inputTotal, outputTotal,
                PickKpis.profitRate(inputTotal, outputTotal), PickKpis.hitRate(hitCount, slipCount),
                maxDrawdown, worstLosingStreak);
    }

    /** {@code profitRate} with nulls flattened to zero, for comparisons that cannot carry a null. */
    public BigDecimal profitRateOrZero() {
        return profitRate == null ? BigDecimal.ZERO : profitRate;
    }
}
