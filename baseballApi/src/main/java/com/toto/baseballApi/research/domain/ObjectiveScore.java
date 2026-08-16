package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * How one candidate is ranked against another: excess return over the market's own expectation,
 * behind a minimum-sample gate.
 *
 * <p>Ranking on {@link BacktestMetrics#legExcessReturn()} rather than raw profit rate is what keeps the
 * search pointed at the same thing the goal measures. Raw ROI also ranks leg counts against each
 * other unfairly — a 3-leg slip carries the house margin three times, so it starts a whole segment
 * behind a 2-leg slip regardless of how well it picks. The benchmark falls with the leg count too,
 * so subtracting it puts every candidate on one scale.
 *
 * <p>The sample gate is the other half. Excess return on a handful of slips is mostly luck, and an
 * unguarded sweep reliably crowns the candidate that bet four times, hit three, and shows +400% —
 * a result that will never reproduce. Candidates under the gate are not deleted (seeing them is
 * useful) but they sort below every qualified candidate no matter how good their headline number
 * looks, so they can never win a search.
 *
 * <p>Ties break toward the larger sample, because between two candidates showing the same edge the
 * better-evidenced one is the better bet.
 */
public record ObjectiveScore(boolean qualified, BigDecimal excessReturn, int slipCount) {

    public static final Comparator<ObjectiveScore> BEST_FIRST =
            Comparator.comparing(ObjectiveScore::qualified)
                    .thenComparing(ObjectiveScore::excessReturn)
                    .thenComparingInt(ObjectiveScore::slipCount)
                    .reversed();

    public static ObjectiveScore of(BacktestMetrics metrics, int minSlipCount) {
        return new ObjectiveScore(
                metrics.slipCount() >= minSlipCount,
                metrics.legExcessReturnOrZero(),
                metrics.slipCount());
    }
}
