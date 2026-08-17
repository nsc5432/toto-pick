package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class FavoriteTwoWaySlipAlgorithmTest {

    private final FavoriteTwoWaySlipAlgorithm algorithm = new FavoriteTwoWaySlipAlgorithm();

    /** The 3-way listing: the day universe this algorithm iterates, and what FAVORITE selects on. */
    private BaseballResult threeWay(int id, String home, String away,
            Double pubHome, Double pubDraw, Double pubAway, String totalResult, Double totalDiv) {
        return new BaseballResult(id, 2026, 76, "KBO", "260630", "18:30", home, away,
                "야구 승1패", null, 0.0, null, totalResult, totalDiv,
                pubHome, pubDraw, pubAway);
    }

    /**
     * The 2-way listing of the same fixture. It publishes home and away and leaves the middle slot
     * null, because 승패 has no middle slot — that null is the market, not missing data.
     */
    private BaseballResult twoWay(int id, String home, String away,
            Double pubHome, Double pubAway, String totalResult, Double totalDiv) {
        return new BaseballResult(id, 2026, 76, "KBO", "260630", "18:30", home, away,
                "야구 승패", null, 0.0, null, totalResult, totalDiv,
                pubHome, null, pubAway);
    }

    private SlipSelectionInput input(int combinedN,
            List<BaseballResult> threeWayGames, List<BaseballResult> twoWayGames) {
        return new SlipSelectionInput("260630", 10, 1.8, 2.5, combinedN,
                threeWayGames, List.of(), TeamFormIndex.empty(),
                MarketPairIndex.build(twoWayGames), AlgorithmParams.empty());
    }

    /** Settlement must be able to resolve both markets' rows by id. */
    private Map<Integer, BaseballResult> byId(
            List<BaseballResult> threeWayGames, List<BaseballResult> twoWayGames) {
        Map<Integer, BaseballResult> byId = new LinkedHashMap<>();
        for (BaseballResult game : threeWayGames) {
            byId.put(game.id(), game);
        }
        for (BaseballResult game : twoWayGames) {
            byId.put(game.id(), game);
        }
        return byId;
    }

    @Test
    void picksTheTwoWayRowAtItsOwnPublishedPrice() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "승", 1.80),
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "패", 1.70));
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", 1.52, 2.09, "승", 1.52),
                twoWay(102, "C", "D", 2.09, 1.52, "패", 1.52));

        List<PickSlip> slips = algorithm.selectSlips(input(2, threeWayGames, twoWayGames));

        assertThat(slips).hasSize(1);
        List<PickSelection> legs = slips.get(0).selections();
        // Legs point at the 승패 rows — that is what settlement has to resolve.
        assertThat(legs).extracting(PickSelection::resultId).containsExactlyInAnyOrder(101, 102);
        assertThat(legs).extracting(PickSelection::predictedTotalResult)
                .containsExactlyInAnyOrder("승", "패");
        // The price carried down is the 승패 row's own quote, not the 3-way one it was grouped with.
        assertThat(legs).allSatisfy(leg ->
                assertThat(leg.price().odds()).isEqualTo(1.52));
    }

    @Test
    void backsWhicheverSideTheTwoWayMarketItselfFavors() {
        // The two markets disagree on the favorite in about 14% of games: 승1패 prices "2점차 이상"
        // while 승패 prices the outright winner, and home advantage lives disproportionately in the
        // one-run games 승1패 sets aside. When they disagree, this algorithm follows its own market.
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 2.45, 3.20, 2.36, "승", 2.45));   // 3-way favors away
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", 1.65, 1.89, "승", 1.65));         // 승패 favors home

        assertThat(new FavoriteOddsSlipAlgorithm()
                .selectSlips(input(1, threeWayGames, twoWayGames)).get(0).selections())
                .extracting(PickSelection::predictedTotalResult).containsExactly("패");
        assertThat(algorithm.selectSlips(input(1, threeWayGames, twoWayGames)).get(0).selections())
                .extracting(PickSelection::predictedTotalResult).containsExactly("승");
    }

    @Test
    void aOneRunWinIsAHitHereAndAMissInTheThreeWayMarket() {
        // The entire case for the switch, as one comparison: both games were decided by one run, so
        // 승1패 settles them into the middle slot and the favorite backer loses both.
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "1", 3.40),
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "1", 3.30));
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", 1.52, 2.09, "승", 1.52),
                twoWay(102, "C", "D", 2.09, 1.52, "패", 1.52));
        Map<Integer, BaseballResult> gamesById = byId(threeWayGames, twoWayGames);

        List<SettledSlip> twoWaySlips = PickBacktester.runDay(
                algorithm, input(2, threeWayGames, twoWayGames), gamesById, BigDecimal.valueOf(1000));
        List<SettledSlip> threeWaySlips = PickBacktester.runDay(
                new FavoriteOddsSlipAlgorithm(), input(2, threeWayGames, twoWayGames),
                gamesById, BigDecimal.valueOf(1000));

        assertThat(twoWaySlips).singleElement().matches(SettledSlip::hit);
        assertThat(threeWaySlips).singleElement().matches(slip -> !slip.hit());
        // Paid at the 승패 rows' own TOTAL_DIV: ceil2(1.52 × 1.52) = 2.32 → 2,320원.
        assertThat(twoWaySlips.get(0).outputMoney()).isEqualByComparingTo("2320");
    }

    @Test
    void benchmarksTheLegAgainstTheTwoWayMarginNotTheThreeWayOne() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "승", 1.80),
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "패", 1.70));
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", 1.52, 2.09, "승", 1.52),
                twoWay(102, "C", "D", 2.09, 1.52, "패", 1.52));

        LegTally legs = PickBacktester.runDay(
                        algorithm, input(2, threeWayGames, twoWayGames),
                        byId(threeWayGames, twoWayGames), BigDecimal.valueOf(1000))
                .get(0).legs();

        assertThat(legs.count()).isEqualTo(2);
        assertThat(legs.hitCount()).isEqualTo(2);
        // 1/1.52 + 1/2.09 = 1.1364, so 1/1.1364 = 0.8800 per leg — the cheaper 2-way margin, not
        // the 3-way 1/1.15. Switching to a cheaper market is a real gain and has to show up here.
        assertThat(legs.benchmarkTotal().doubleValue()).isCloseTo(
                2 * 0.8800, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void skipsGamesThatCannotBeSettledOrPricedInTheTwoWayMarket() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "승", 1.80),   // pairs, and is priced
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "패", 1.70),   // no 승패 listing
                threeWay(3, "E", "F", 1.95, 3.30, 2.10, "승", 1.95));  // 승패 listed, but unpriced
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", 1.52, 2.09, "승", 1.52),
                twoWay(103, "E", "F", null, null, "승", 1.60));

        // Only one candidate survives, so no complete 2-leg slip can be formed.
        assertThat(algorithm.selectSlips(input(2, threeWayGames, twoWayGames))).isEmpty();
        assertThat(algorithm.selectSlips(input(1, threeWayGames, twoWayGames)))
                .singleElement()
                .satisfies(slip -> assertThat(slip.selections())
                        .extracting(PickSelection::resultId).containsExactly(101));
    }

    @Test
    void ordersLegsByTheBackedPriceAndChunksByCombinedN() {
        List<BaseballResult> threeWayGames = new ArrayList<>();
        List<BaseballResult> twoWayGames = new ArrayList<>();
        // Increasingly long home favorites on the 승패 side: 1.30 → 1.52 → 1.65 → 1.73.
        double[][] twoWayPrices = {{1.30, 4.20}, {1.52, 2.09}, {1.65, 1.89}, {1.73, 1.79}};
        for (int i = 0; i < twoWayPrices.length; i++) {
            threeWayGames.add(threeWay(i + 1, "H" + i, "V" + i, 1.80, 3.40, 2.20, "승", 1.80));
            twoWayGames.add(twoWay(
                    101 + i, "H" + i, "V" + i, twoWayPrices[i][0], twoWayPrices[i][1], "승", 1.5));
        }

        List<PickSlip> slips = algorithm.selectSlips(input(2, threeWayGames, twoWayGames));

        assertThat(slips).hasSize(2);
        // Shortest two first — the highest-probability combination, as on the 3-way path.
        assertThat(slips.get(0).selections()).extracting(PickSelection::resultId)
                .containsExactly(101, 102);
        assertThat(slips.get(1).selections()).extracting(PickSelection::resultId)
                .containsExactly(103, 104);
    }

    @Test
    void declaresEveryThresholdItUses() {
        // One knob left. `oneRunHomeShare` was the estimator's, and the estimator is gone.
        assertThat(algorithm.paramSpace().specs()).extracting(ParamSpec::name)
                .containsExactly(AlgorithmParams.COMBINED_N);
    }
}
