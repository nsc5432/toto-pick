package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Leg-level tally: every individual pick, priced and settled as if it stood alone.
 *
 * <p>A slip is the product, but a leg is where the information is. Two runs that back the exact same
 * games differ wildly at slip level purely by how the legs were grouped — a 3-leg parlay's variance
 * is enormous and roughly 100 slips is far too small a sample to see through it. The same run seen
 * leg by leg has three times the sample and no grouping artifact at all, because
 * {@link PickSettlement#marketExpectation} is multiplicative: a slip's benchmark is just the product
 * of its legs' benchmarks, so grouping cannot create or destroy edge, only obscure it.
 *
 * <p>Hence the goal is stated on {@link #excessReturn()}. If the legs carry an edge, any grouping
 * inherits it; if they do not, no grouping can manufacture one.
 *
 * @param payoutTotal    sum of the backed side's decimal odds over winning legs (losing legs pay 0),
 *                       in units of one stake per leg
 * @param benchmarkTotal sum of {@code 1/overround} over the same legs — what those stakes were worth
 *                       under the market's own probabilities, whichever side was backed
 * @param payoutSquareTotal sum of squared leg payouts, carried so {@link #excessTStat()} can be
 *                       computed by simple addition as days are merged
 */
public record LegTally(
        int count,
        int hitCount,
        BigDecimal payoutTotal,
        BigDecimal benchmarkTotal,
        BigDecimal payoutSquareTotal) {

    public static final LegTally EMPTY =
            new LegTally(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private static final int SCALE = 4;

    /** Tally without the squared term — for callers that do not need a significance test. */
    public LegTally(int count, int hitCount, BigDecimal payoutTotal, BigDecimal benchmarkTotal) {
        this(count, hitCount, payoutTotal, benchmarkTotal, null);
    }

    public LegTally plus(LegTally other) {
        return other == null ? this : new LegTally(
                count + other.count,
                hitCount + other.hitCount,
                payoutTotal.add(other.payoutTotal),
                benchmarkTotal.add(other.benchmarkTotal),
                payoutSquareTotal == null || other.payoutSquareTotal == null
                        ? null : payoutSquareTotal.add(other.payoutSquareTotal));
    }

    /**
     * How many standard errors the edge sits above zero — {@code null} when the squared term was not
     * collected or the sample is too small to estimate a spread.
     *
     * <p>A fixed excess-return bar cannot serve as a goal on its own, because the same number means
     * different things at different sample sizes: on 300 legs the noise alone is worth about 7%p,
     * on 900 legs about 4%p. This makes the bar self-calibrating — a strategy that bets often enough
     * can demonstrate a small edge, while one that bets rarely has to show a large one.
     *
     * <p>The benchmark term is treated as fixed: {@code 1/overround} varies between 0.859 and 0.871
     * across this data, so essentially all the variance is in the payouts.
     */
    public BigDecimal excessTStat() {
        Double standardError = standardError();
        return standardError == null ? null
                : BigDecimal.valueOf(excessReturn().doubleValue() / standardError)
                        .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Standard error of the mean leg return, or {@code null} when it cannot be estimated. Exposed so
     * two windows' edges can be compared on the same footing — see
     * {@code ExperimentGoal.maxTrainValidationGapTStat}.
     */
    public Double standardError() {
        if (payoutSquareTotal == null || count < 2) {
            return null;
        }
        double n = count;
        double mean = payoutTotal.doubleValue() / n;
        double variance = (payoutSquareTotal.doubleValue() - n * mean * mean) / (n - 1);
        return variance > 0 ? Math.sqrt(variance / n) : null;
    }

    /** Realized return per staked unit, or {@code null} when nothing was bet. */
    public BigDecimal returnRate() {
        return count == 0 ? null : rate(payoutTotal);
    }

    /** The no-skill return per staked unit — the market's own expectation for these same legs. */
    public BigDecimal benchmarkRate() {
        return count == 0 ? null : rate(benchmarkTotal);
    }

    /**
     * {@code returnRate − benchmarkRate}: the part of the result attributable to the picks rather
     * than to the prices they were made at. Zero means the strategy knew nothing the odds did not.
     */
    public BigDecimal excessReturn() {
        return count == 0 ? null
                : payoutTotal.subtract(benchmarkTotal)
                        .divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal total) {
        return total.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);
    }
}
