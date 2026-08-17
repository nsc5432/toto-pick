package com.toto.baseballApi.baseballresult.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TwoWayOddsEstimatorTest {

    private static final double TOLERANCE = 0.02;

    @Test
    void reproducesTheMeasuredTwoWayOverround() {
        TwoWayOdds odds = TwoWayOddsEstimator.from(new ThreeWayOdds(1.80, 3.40, 2.20), 0.5);

        // 1/승 + 1/패 must come back out at the measured 1.13629 (2-dp rounding aside).
        assertThat(odds.overround() + 1.0)
                .isCloseTo(TwoWayOddsEstimator.PUBLISHED_OVERROUND, within(0.005));
    }

    @Test
    void movesProbabilityFromTheOneRunSlotIntoBothOutrightPrices() {
        ThreeWayOdds threeWay = new ThreeWayOdds(1.80, 3.40, 2.20);
        TwoWayOdds twoWay = TwoWayOddsEstimator.from(threeWay, 0.5);

        // Both prices must shorten: the outright bet wins strictly more often than the 2+ run one.
        assertThat(twoWay.home()).isLessThan(threeWay.home());
        assertThat(twoWay.away()).isLessThan(threeWay.away());

        double expectedHome = threeWay.impliedHome() + 0.5 * threeWay.impliedDraw();
        assertThat(twoWay.impliedHome()).isCloseTo(expectedHome, within(TOLERANCE));
    }

    @Test
    void atAnEvenSplitTheTwoWayFavoriteIsAlwaysTheThreeWayFavorite() {
        // The identity the whole "market switch, not new signal" claim rests on:
        // p2_home − p2_away = p3_home − p3_away when the one-run mass splits evenly.
        double[][] quotes = {
                {1.80, 3.40, 2.20}, {2.20, 3.40, 1.80}, {1.36, 3.90, 3.20},
                {1.99, 3.15, 2.01}, {2.56, 3.60, 1.34}, {1.21, 4.10, 3.23},
        };
        for (double[] quote : quotes) {
            ThreeWayOdds threeWay = new ThreeWayOdds(quote[0], quote[1], quote[2]);
            TwoWayOdds twoWay = TwoWayOddsEstimator.from(threeWay, 0.5);

            assertThat(twoWay.home() < twoWay.away())
                    .as("favorite side for %s", java.util.Arrays.toString(quote))
                    .isEqualTo(threeWay.home() < threeWay.away());
        }
    }

    @Test
    void shiftingTheSplitMovesTheFavoriteTowardsTheFavoredSide() {
        ThreeWayOdds threeWay = new ThreeWayOdds(1.80, 3.40, 2.20);

        TwoWayOdds homeHeavy = TwoWayOddsEstimator.from(threeWay, 0.7);
        TwoWayOdds evenSplit = TwoWayOddsEstimator.from(threeWay, 0.5);
        TwoWayOdds awayHeavy = TwoWayOddsEstimator.from(threeWay, 0.3);

        assertThat(homeHeavy.home()).isLessThan(evenSplit.home());
        assertThat(awayHeavy.home()).isGreaterThan(evenSplit.home());
    }

    @Test
    void rejectsASplitOutsideTheUnitInterval() {
        ThreeWayOdds threeWay = new ThreeWayOdds(1.80, 3.40, 2.20);

        assertThatThrownBy(() -> TwoWayOddsEstimator.from(threeWay, 1.4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TwoWayOddsEstimator.from(threeWay, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsNullForAMissingQuoteRatherThanThrowing() {
        assertThat(TwoWayOddsEstimator.from(null, 0.5)).isNull();
    }

    @Test
    void returnsNullWhenTheFavoriteIsTooShortToQuote() {
        // An implied outright probability above ~0.88 prices below 1.0, which is not a bet.
        assertThat(TwoWayOddsEstimator.from(new ThreeWayOdds(1.02, 30.0, 40.0), 0.5)).isNull();
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
