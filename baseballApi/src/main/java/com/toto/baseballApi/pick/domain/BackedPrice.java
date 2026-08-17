package com.toto.baseballApi.pick.domain;

/**
 * The pre-game price of the one side a leg backs, plus the margin of the market it was quoted in.
 *
 * <p>Settlement can normally read this straight off the row's own published quote, so most algorithms
 * have no reason to supply it. Attaching it is how an algorithm says "this is the price I actually
 * took", which matters whenever selection and settlement look at different rows: a 승패 algorithm
 * selects over the 승1패 day universe and points its legs at the paired 승패 row, and carrying the price
 * down with the selection is what guarantees the number that sized the stake is the number the
 * benchmark uses.
 *
 * <p>It was originally load-bearing rather than a convenience: 승패 rows had no {@code PUB_*} at all
 * and their price had to be derived from the 3-way quote. Published 2-way prices now exist, so this
 * carries a real quote instead of an estimate.
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
