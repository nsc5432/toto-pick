package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * How one candidate is ranked against another: validation profit rate, behind a minimum-sample gate.
 *
 * <p>The gate is the whole point. Profit rate on a handful of slips is mostly luck, and an
 * unguarded sweep reliably crowns the candidate that bet four times, hit three, and shows +400% —
 * a result that will never reproduce. Candidates under the gate are not deleted (seeing them is
 * useful) but they sort below every qualified candidate no matter how good their headline number
 * looks, so they can never win a search.
 *
 * <p>Ties on profit rate break toward the larger sample, because between two candidates earning the
 * same rate the better-evidenced one is the better bet.
 */
public record ObjectiveScore(boolean qualified, BigDecimal profitRate, int slipCount) {

    public static final Comparator<ObjectiveScore> BEST_FIRST =
            Comparator.comparing(ObjectiveScore::qualified)
                    .thenComparing(ObjectiveScore::profitRate)
                    .thenComparingInt(ObjectiveScore::slipCount)
                    .reversed();

    public static ObjectiveScore of(BacktestMetrics metrics, int minSlipCount) {
        return new ObjectiveScore(
                metrics.slipCount() >= minSlipCount,
                metrics.profitRateOrZero(),
                metrics.slipCount());
    }
}
