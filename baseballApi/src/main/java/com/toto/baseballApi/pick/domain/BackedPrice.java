package com.toto.baseballApi.pick.domain;

/**
 * The pre-game price of the one side a leg backs, plus the margin of the market it was quoted in.
 *
 * <p>Normally settlement reads this straight off {@code BaseballResult.publishedOdds()}, and an
 * algorithm has no reason to supply it. It exists for the case where the row being settled carries no
 * published quote of its own: the 2-way ("야구 승패") rows have no {@code PUB_*} columns, so an
 * algorithm betting that market derives the price from the same fixture's published 3-way quote (see
 * {@code TwoWayOddsEstimator}) and hands the result down with the selection.
 *
 * <p>{@code overround} follows the same convention as {@code ThreeWayOdds.overround()} — the inverse
 * sum <em>minus one</em>, i.e. the margin — because {@code PickSettlement.marketExpectation} divides
 * by {@code 1 + overround}. Passing the inverse sum itself would silently deflate every benchmark.
 *
 * @param odds      decimal odds of the backed side, as quoted before the game
 * @param overround the market's margin: {@code Σ(1/odds) − 1} over all sides of that market
 */
public record BackedPrice(double odds, double overround) {

    public BackedPrice {
        if (!(odds > 1.0)) {
            throw new IllegalArgumentException("Backed odds must exceed 1.0: " + odds);
        }
        if (!(overround > -1.0)) {
            throw new IllegalArgumentException("Overround must exceed -1.0: " + overround);
        }
    }
}
