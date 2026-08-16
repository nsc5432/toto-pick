package com.toto.baseballApi.baseballresult.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ThreeWayOddsTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void overroundIsTheExcessOfTheInverseOddsOverOne() {
        // 1/2.0 + 1/3.5 + 1/3.0 = 1.119047..., i.e. an 11.9% house margin.
        assertThat(new ThreeWayOdds(2.0, 3.5, 3.0).overround())
                .isCloseTo(0.1190476190, within(1e-9));
    }

    @Test
    void impliedProbabilitiesSumToExactlyOne() {
        ThreeWayOdds odds = new ThreeWayOdds(1.8, 3.6, 4.2);

        assertThat(odds.impliedHome() + odds.impliedDraw() + odds.impliedAway())
                .isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void decisiveIsEverythingOutsideTheDrawSlot() {
        ThreeWayOdds odds = new ThreeWayOdds(2.0, 3.5, 3.0);

        assertThat(odds.impliedDecisive())
                .isCloseTo(odds.impliedHome() + odds.impliedAway(), within(TOLERANCE));
    }

    @Test
    void ofYieldsNullWhenAnySideIsMissingOrNotAPrice() {
        assertThat(ThreeWayOdds.of(null, 3.5, 3.0)).isNull();
        assertThat(ThreeWayOdds.of(2.0, null, 3.0)).isNull();
        assertThat(ThreeWayOdds.of(2.0, 3.5, null)).isNull();
        // A price of 1.0 returns the stake and nothing more — not a real quote.
        assertThat(ThreeWayOdds.of(1.0, 3.5, 3.0)).isNull();
    }

    @Test
    void constructorRejectsNonPrices() {
        assertThatThrownBy(() -> new ThreeWayOdds(1.0, 3.5, 3.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
