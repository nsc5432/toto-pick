package com.toto.baseballApi.baseballresult.domain;

/**
 * The two published prices of one 2-way ("야구 승패") game, plus the de-vig that turns them into
 * probabilities. The 2-way counterpart of {@link ThreeWayOdds}.
 *
 * <p>The two markets are not the same bet on the same game. In 승1패 the outcome slots are "홈 2점차
 * 이상 승 / 1점차 경기 / 원정 2점차 이상 승", so a favorite backer loses every one-run game — the
 * middle slot is not a draw, it is roughly a quarter of all games. In 승패 there is no middle slot:
 * {@code 승}/{@code 패} is the outright winner, so a one-run win is a hit. That is the entire reason
 * this record exists.
 *
 * <p>The measured 2-way overround pools to 1.1412 across 2,676 published games — tighter than the
 * 3-way 1.1519, so the no-skill leg return improves from −13.2% to −12.4%. Still negative: the market
 * is cheaper here, not beatable.
 *
 * <p>It is not a constant, which is why {@link #overround()} computes it per game instead of taking
 * one. It drifted from about 1.143 over 260421~260712 down to 1.136 over 260713~260817, and the
 * 260818 listings are at 1.136 — a 0.7%p move, i.e. the same size as the edges being hunted. Anything
 * that assumes a fixed margin mismeasures the benchmark by about as much as the signal is worth.
 *
 * <p>This record used to be filled by an estimator that split the 승1패 one-run slot between the two
 * sides, because 승패 rows carried no {@code PUB_*} of their own. Published 2-way prices now cover the
 * range and the estimator is gone. It is worth knowing why it had to go rather than stay as a
 * fallback: measured against the real prices it was only 1.5–1.7% off on the number, but it named a
 * different favorite in 14.4% of games — the price was approximately right and the *pick* was wrong
 * one game in seven.
 */
public record TwoWayOdds(double home, double away) {

    public TwoWayOdds {
        if (!(home > 1.0) || !(away > 1.0)) {
            throw new IllegalArgumentException("Odds must both exceed 1.0: " + home + "/" + away);
        }
    }

    /** {@code null} unless both prices are present — a one-sided quote cannot be de-vigged. */
    public static TwoWayOdds of(Double home, Double away) {
        if (home == null || away == null || home <= 1.0 || away <= 1.0) {
            return null;
        }
        return new TwoWayOdds(home, away);
    }

    /** {@code 1/home + 1/away − 1} — the house margin baked into this game's prices. */
    public double overround() {
        return inverseSum() - 1.0;
    }

    public double impliedHome() {
        return (1.0 / home) / inverseSum();
    }

    public double impliedAway() {
        return (1.0 / away) / inverseSum();
    }

    private double inverseSum() {
        return 1.0 / home + 1.0 / away;
    }
}
