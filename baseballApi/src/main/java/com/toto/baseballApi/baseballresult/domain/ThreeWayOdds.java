package com.toto.baseballApi.baseballresult.domain;

/**
 * The three published prices of one 3-way ("야구 승1패") game, plus the de-vig that turns them into
 * probabilities.
 *
 * <p>Decimal odds are not probabilities: the three inverses sum to more than 1, and the excess is
 * the bookmaker's margin rather than information. {@link #overround()} is that excess and the hurdle
 * any strategy has to clear; the {@code implied*} accessors rescale to sum to exactly 1, which is
 * the market's actual estimate.
 *
 * <p>The de-vig lives here rather than in each algorithm so every family measures the same margin
 * the same way — an algorithm comparing its own estimate against a differently-normalised market
 * price is measuring its own normalisation.
 */
public record ThreeWayOdds(double home, double draw, double away) {

    public ThreeWayOdds {
        if (!(home > 1.0) || !(draw > 1.0) || !(away > 1.0)) {
            throw new IllegalArgumentException(
                    "Odds must all exceed 1.0: " + home + "/" + draw + "/" + away);
        }
    }

    /** {@code null} unless all three prices are present — a partial quote cannot be de-vigged. */
    static ThreeWayOdds of(Double home, Double draw, Double away) {
        if (home == null || draw == null || away == null
                || home <= 1.0 || draw <= 1.0 || away <= 1.0) {
            return null;
        }
        return new ThreeWayOdds(home, draw, away);
    }

    /** {@code 1/home + 1/draw + 1/away − 1} — the house margin baked into this game's prices. */
    public double overround() {
        return inverseSum() - 1.0;
    }

    public double impliedHome() {
        return (1.0 / home) / inverseSum();
    }

    public double impliedDraw() {
        return (1.0 / draw) / inverseSum();
    }

    public double impliedAway() {
        return (1.0 / away) / inverseSum();
    }

    /** Probability the game is decided by 2+ runs, i.e. everything the draw slot does not hold. */
    public double impliedDecisive() {
        return 1.0 - impliedDraw();
    }

    private double inverseSum() {
        return 1.0 / home + 1.0 / draw + 1.0 / away;
    }
}
