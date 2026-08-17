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
 */
public record TwoWayOdds(double home, double away) {

    public TwoWayOdds {
        if (!(home > 1.0) || !(away > 1.0)) {
            throw new IllegalArgumentException("Odds must both exceed 1.0: " + home + "/" + away);
        }
    }

    /** {@code null} unless both prices are present — a one-sided quote cannot be de-vigged. */
    static TwoWayOdds of(Double home, Double away) {
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
