package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class MarketPairIndexTest {

    private BaseballResult game(int id, int round, String gameType, String tm, String away) {
        return new BaseballResult(id, 2026, round, "KBO", "260630", tm, "A", away,
                gameType, null, 0.0, null, "승", 1.5,
                null, null, null, null, null, null);
    }

    @Test
    void pairsTheSameFixtureAcrossTheTwoMarkets() {
        BaseballResult twoWay = game(200, 76, "야구 승패", "18:30", "B");
        MarketPairIndex index = MarketPairIndex.build(List.of(twoWay));

        assertThat(index.pairOf(game(100, 76, "야구 승1패", "18:30", "B"))).isEqualTo(twoWay);
    }

    @Test
    void separatesListingsOfTheSameFixtureInDifferentRounds() {
        // The same game routinely appears in two consecutive 회차 at different prices; a leg picked
        // from one 회차 must settle against that 회차's own 승패 row.
        BaseballResult round76 = game(200, 76, "야구 승패", "18:30", "B");
        BaseballResult round77 = game(201, 77, "야구 승패", "18:30", "B");
        MarketPairIndex index = MarketPairIndex.build(List.of(round76, round77));

        assertThat(index.pairOf(game(100, 76, "야구 승1패", "18:30", "B"))).isEqualTo(round76);
        assertThat(index.pairOf(game(101, 77, "야구 승1패", "18:30", "B"))).isEqualTo(round77);
    }

    @Test
    void returnsNullWhenTheFixtureWasNeverListedInTheTwoWayMarket() {
        MarketPairIndex index = MarketPairIndex.build(
                List.of(game(200, 76, "야구 승패", "18:30", "B")));

        assertThat(index.pairOf(game(100, 76, "야구 승1패", "18:30", "C"))).isNull();
        assertThat(index.pairOf(null)).isNull();
    }

    @Test
    void resolvesDuplicateListingsOfOneFixtureToTheLowestId() {
        // The unique key also includes COND, so one fixture can carry more than one row.
        MarketPairIndex index = MarketPairIndex.build(List.of(
                game(205, 76, "야구 승패", "18:30", "B"),
                game(200, 76, "야구 승패", "18:30", "B")));

        assertThat(index.pairOf(game(100, 76, "야구 승1패", "18:30", "B")).id()).isEqualTo(200);
    }

    @Test
    void anEmptyIndexPairsNothing() {
        assertThat(MarketPairIndex.empty().pairOf(game(100, 76, "야구 승1패", "18:30", "B"))).isNull();
        assertThat(MarketPairIndex.build(List.of()).pairOf(game(100, 76, "야구 승1패", "18:30", "B")))
                .isNull();
    }
}
