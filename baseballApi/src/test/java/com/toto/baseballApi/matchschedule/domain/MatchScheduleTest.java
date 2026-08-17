package com.toto.baseballApi.matchschedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.FixtureKey;

class MatchScheduleTest {

    private MatchSchedule scheduled(Double pubHome, Double pubDraw, Double pubAway) {
        return new MatchSchedule(7, 2026, 76, "KBO", "260901", "18:30", "A", "B",
                "야구 승1패", null, pubHome, pubDraw, pubAway);
    }

    @Test
    void carriesThePublishedPriceSoAlgorithmsCanSelectOnItUnchanged() {
        BaseballResult fixture = scheduled(1.80, 3.40, 2.20).asFixture();

        assertThat(fixture.publishedOdds()).isNotNull();
        assertThat(fixture.publishedOdds().home()).isEqualTo(1.80);
        assertThat(fixture.publishedOdds().away()).isEqualTo(2.20);
    }

    @Test
    void hasNoOutcomeBecauseTheGameHasNotBeenPlayed() {
        BaseballResult fixture = scheduled(1.80, 3.40, 2.20).asFixture();

        // The point of a forward pick: there is nothing here that could encode the result.
        assertThat(fixture.totalResult()).isNull();
        assertThat(fixture.totalDiv()).isNull();
        assertThat(fixture.res1()).isNull();
        assertThat(fixture.res2()).isNull();
    }

    @Test
    void isIdentifiedByTheSameFixtureKeySettlementWillLookUp() {
        MatchSchedule schedule = scheduled(1.80, 3.40, 2.20);

        BaseballResult playedLater = new BaseballResult(
                999, 2026, 76, "KBO", "260901", "18:30", "A", "B",
                "야구 승1패", null, 3.0, 1.0, "승", 1.80,
                1.80, 3.40, 2.20);

        // The join that lets a pick made before the game settle against the row created after it.
        assertThat(FixtureKey.of(schedule.asFixture())).isEqualTo(FixtureKey.of(playedLater));
    }

    @Test
    void anUnpricedFixtureIsSkippableRatherThanBroken() {
        assertThat(scheduled(null, null, null).asFixture().publishedOdds()).isNull();
        assertThat(scheduled(1.80, null, 2.20).asFixture().publishedOdds()).isNull();
    }
}
