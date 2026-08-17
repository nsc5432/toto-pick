package com.toto.baseballApi.research.domain;

import java.util.List;

/**
 * How well {@code TwoWayOddsEstimator} reproduces real 승패 prices, and what switching markets does
 * to the hit rate — measured over real games rather than argued from the formula.
 *
 * <p>The estimator is a model, and everything downstream of it (martingale stake sizing, every leg
 * benchmark) inherits its error silently: a biased price does not fail loudly, it just quietly makes
 * "hit the slip, missed the target" more common. So it gets validated before its numbers are trusted,
 * the same way the backfill's error was measured (home 6.1% / away 6.2% / draw 11.9%,
 * docs/statistical-model-design.md).
 *
 * <p>Validation is possible with no extra data because a 승패 row's {@code TOTAL_DIV} is the genuine
 * published price of the side that won — the 3-way {@code PUB_*} columns cross-checked against
 * {@code TOTAL_DIV} on every row and matched. Comparing an estimate to it in aggregate is
 * measurement, not selection, so 설계 결정 D2 does not apply; it would only apply if a strategy read
 * that column to decide something.
 *
 * @param publishedGames         3-way games in range carrying a published price
 * @param pairedGames            of those, how many have a 승패 counterpart that settled — the rest
 *                               are simply unavailable to a 2-way strategy
 * @param comparableGames        of those, how many the estimator could quote
 * @param meanAbsRelativeError   mean {@code |estimate − actual| / actual} on the realized side
 * @param meanSignedRelativeError same, signed — separates a systematic bias from random spread
 * @param twoWayFavoriteHitRate  how often the backed favorite won outright (승패 settlement)
 * @param threeWayFavoriteHitRate the same picks on the same games under 승1패 settlement, where a
 *                               one-run win is a loss. The gap between the two is the entire case for
 *                               switching markets
 * @param bySide                 error split by which side actually won, so a directional bias shows
 * @param byPriceBucket          error and hit rate by the favorite's estimated price
 */
public record TwoWayOddsCheck(
        double oneRunHomeShare,
        int publishedGames,
        int pairedGames,
        int comparableGames,
        double meanAbsRelativeError,
        double meanSignedRelativeError,
        double twoWayFavoriteHitRate,
        double threeWayFavoriteHitRate,
        List<SideError> bySide,
        List<PriceBucket> byPriceBucket) {

    /** @param side the realized outcome, {@code 승} or {@code 패} */
    public record SideError(
            String side,
            int count,
            double meanAbsRelativeError,
            double meanSignedRelativeError) {
    }

    public record PriceBucket(
            String label,
            int count,
            double meanEstimatedFavoriteOdds,
            double twoWayFavoriteHitRate,
            double threeWayFavoriteHitRate,
            double meanAbsRelativeError) {
    }
}
