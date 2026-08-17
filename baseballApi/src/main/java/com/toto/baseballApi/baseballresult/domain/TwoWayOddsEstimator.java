package com.toto.baseballApi.baseballresult.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Derives a 2-way ("야구 승패") pre-game price from the same fixture's published 3-way ("야구 승1패")
 * price.
 *
 * <p><strong>Why this exists.</strong> {@code PUB_HOME_DIV/PUB_DRAW_DIV/PUB_AWAY_DIV} were collected
 * for 승1패 rows only, so 승패 rows carry no pre-game price at all — only {@code TOTAL_DIV}, the
 * measured payout of the side that actually won. Sizing a stake or quoting a benchmark off
 * {@code TOTAL_DIV} is exactly the bias 설계 결정 D2 rejected: only the realized side is measured, so
 * a rule reading it partly learns the outcome. This estimator reads nothing but published pre-game
 * numbers, which makes the leak structurally impossible rather than a rule to remember.
 *
 * <p><strong>The formula.</strong> The 승1패 slots partition the same sample space the 승패 slots do —
 * a 1점차 game is a game one of the two teams won by exactly one run. So the outright win probability
 * is the 2+ run probability plus this side's share of the one-run mass:
 *
 * <pre>
 *   p2_home = impliedHome + s · impliedDraw
 *   p2_away = impliedAway + (1 − s) · impliedDraw      (p2_home + p2_away = 1)
 *   odds_home = 1 / (p2_home × {@value #PUBLISHED_OVERROUND})
 * </pre>
 *
 * <p>{@code s} is the fraction of one-run games won by the home side. It is a caller-supplied knob
 * rather than a constant here precisely so it can be swept: a baked-in threshold is never validated.
 *
 * <p>The obvious prior is 0.5 — a one-run game is close to a coin flip — and it is measurably wrong.
 * Against 2,318 realized 승패 payouts the mean absolute price error falls from 5.17% at {@code s=0.5}
 * to 1.60% at {@value #DEFAULT_ONE_RUN_HOME_SHARE}, and the per-side bias (+5.59% on 승, −4.65% on 패
 * at 0.5) crosses zero at the same point (+0.10% / +0.79%). Home teams win about 60% of one-run
 * games — home advantage concentrated in the games decided last. This was calibrated against
 * published <em>prices</em>, never against results, so no outcome enters the fit. The caveat is that
 * only the winning side's price is observable, so the sample is outcome-conditioned; the mitigation
 * is that the value stays swept and is chosen on train excess return like any other knob, with this
 * measurement only deciding where to centre the range.
 *
 * <p>At exactly 0.5 the formula has a property worth knowing even though it is not the calibrated
 * value: {@code p2_home − p2_away = impliedHome − impliedAway}, so the 2-way favorite is then always
 * the same side as the 3-way favorite. That makes 0.5 the reference point at which switching markets
 * changes nothing but the market. Above it the home side takes more of the one-run mass, so a
 * narrowly away-favored game can flip — correctly, since the outright market really does favor home
 * more than the 2+ run market does.
 *
 * <p><strong>The overround.</strong> {@value #PUBLISHED_OVERROUND} is measured, not assumed: across
 * 24 published 승패 games (2026-08-14~15, MLB 15 / NPB 6 / KBO 3) {@code 1/승 + 1/패} averaged
 * 1.13629 with a range of 1.13516–1.13769 and sd ≈ 0.0007, independent of league and price level
 * (1.21/3.23 → 1.1360; 2.56/1.34 → 1.1369). It is a fixed house margin, the 2-way sibling of the
 * 3-way 1.15. Note this is materially tighter than the 1.14 that
 * {@code BaseballResultDivBackfillService} had reverse-engineered from realized payouts.
 *
 * <p><strong>This is a model, not a quote.</strong> The output is used for stake sizing and for the
 * market benchmark; payouts always settle at the real {@code TOTAL_DIV}. Sizing off a wrong price
 * produces "hit the slip, missed the target", so the estimate must be validated against realized
 * {@code TOTAL_DIV} before any result computed on top of it is trusted.
 */
public final class TwoWayOddsEstimator {

    /** Measured 2-way overround {@code 1/승 + 1/패}; see the class javadoc for the sample. */
    public static final double PUBLISHED_OVERROUND = 1.13629;

    /**
     * Share of one-run games won by the home side, calibrated to published 승패 prices (1.60% mean
     * absolute error against 2,318 games, versus 5.17% at the coin-flip 0.5). See the class javadoc.
     */
    public static final double DEFAULT_ONE_RUN_HOME_SHARE = 0.60;

    private TwoWayOddsEstimator() {
    }

    /**
     * The estimated pre-game 승패 price, or {@code null} when the inputs cannot produce a quotable
     * one (either side priced at or below 1.0). Rounded to 2 decimals because that is how the market
     * publishes, which also absorbs the small game-to-game jitter in the overround.
     *
     * @param oneRunHomeShare fraction of one-run games won by the home side, in {@code [0, 1]}
     */
    public static TwoWayOdds from(ThreeWayOdds threeWay, double oneRunHomeShare) {
        if (threeWay == null) {
            return null;
        }
        if (!(oneRunHomeShare >= 0.0) || !(oneRunHomeShare <= 1.0)) {
            throw new IllegalArgumentException("oneRunHomeShare must be in [0, 1]: " + oneRunHomeShare);
        }
        double oneRun = threeWay.impliedDraw();
        double homeProbability = threeWay.impliedHome() + oneRunHomeShare * oneRun;
        double awayProbability = threeWay.impliedAway() + (1.0 - oneRunHomeShare) * oneRun;

        return TwoWayOdds.of(price(homeProbability), price(awayProbability));
    }

    /** {@code null} rather than an out-of-range price, so the caller skips the game. */
    private static Double price(double probability) {
        if (!(probability > 0.0)) {
            return null;
        }
        double odds = 1.0 / (probability * PUBLISHED_OVERROUND);
        if (!Double.isFinite(odds)) {
            return null;
        }
        double rounded = BigDecimal.valueOf(odds)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        return rounded > 1.0 ? rounded : null;
    }
}
