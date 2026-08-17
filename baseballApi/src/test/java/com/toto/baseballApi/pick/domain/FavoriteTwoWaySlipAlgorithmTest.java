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

    /** The 3-way listing: published prices are the only selection signal. */
    private BaseballResult threeWay(int id, String home, String away,
            Double pubHome, Double pubDraw, Double pubAway, String totalResult, Double totalDiv) {
        return new BaseballResult(id, 2026, 76, "KBO", "260630", "18:30", home, away,
                "야구 승1패", null, 0.0, null, totalResult, totalDiv,
                null, null, null, pubHome, pubDraw, pubAway);
    }

    /** The 2-way listing of the same fixture: no published price, only what the winner paid. */
    private BaseballResult twoWay(int id, String home, String away, String totalResult, Double totalDiv) {
        return new BaseballResult(id, 2026, 76, "KBO", "260630", "18:30", home, away,
                "야구 승패", null, 0.0, null, totalResult, totalDiv,
                null, null, null, null, null, null);
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
    void picksTheTwoWayRowWhileReadingTheThreeWayPrice() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "승", 1.80),
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "패", 1.70));
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", "승", 1.55),
                twoWay(102, "C", "D", "패", 1.48));

        List<PickSlip> slips = algorithm.selectSlips(input(2, threeWayGames, twoWayGames));

        assertThat(slips).hasSize(1);
        List<PickSelection> legs = slips.get(0).selections();
        // Legs point at the 승패 rows — that is what settlement has to resolve.
        assertThat(legs).extracting(PickSelection::resultId).containsExactlyInAnyOrder(101, 102);
        // Same sides the 3-way favorite rule would back.
        assertThat(legs).extracting(PickSelection::predictedTotalResult)
                .containsExactlyInAnyOrder("승", "패");
        // Each leg carries its own estimated price, since the 승패 row has none of its own.
        assertThat(legs).allSatisfy(leg -> {
            assertThat(leg.price()).isNotNull();
            assertThat(leg.price().odds()).isLessThan(2.0).isGreaterThan(1.0);
        });
    }

    @Test
    void aOneRunWinIsAHitHereAndAMissInTheThreeWayMarket() {
        // The entire case for the switch, as one comparison: both games were decided by one run, so
        // 승1패 settles them into the middle slot and the favorite backer loses both.
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "1", 3.40),
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "1", 3.30));
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", "승", 1.55),
                twoWay(102, "C", "D", "패", 1.48));
        Map<Integer, BaseballResult> gamesById = byId(threeWayGames, twoWayGames);

        List<SettledSlip> twoWaySlips = PickBacktester.runDay(
                algorithm, input(2, threeWayGames, twoWayGames), gamesById, BigDecimal.valueOf(1000));
        List<SettledSlip> threeWaySlips = PickBacktester.runDay(
                new FavoriteOddsSlipAlgorithm(), input(2, threeWayGames, twoWayGames),
                gamesById, BigDecimal.valueOf(1000));

        assertThat(twoWaySlips).singleElement().matches(SettledSlip::hit);
        assertThat(threeWaySlips).singleElement().matches(slip -> !slip.hit());
        // Paid at the 승패 rows' own TOTAL_DIV: ceil2(1.55 × 1.48) = 2.30 → 2,300원.
        assertThat(twoWaySlips.get(0).outputMoney()).isEqualByComparingTo("2300");
    }

    @Test
    void benchmarksTheLegAgainstTheTwoWayMarginNotTheThreeWayOne() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "승", 1.80),
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "패", 1.70));
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, "A", "B", "승", 1.55),
                twoWay(102, "C", "D", "패", 1.48));

        LegTally legs = PickBacktester.runDay(
                        algorithm, input(2, threeWayGames, twoWayGames),
                        byId(threeWayGames, twoWayGames), BigDecimal.valueOf(1000))
                .get(0).legs();

        assertThat(legs.count()).isEqualTo(2);
        assertThat(legs.hitCount()).isEqualTo(2);
        // 1/1.13629 = 0.8801 per leg — the cheaper 2-way margin, not the 3-way 1/1.15.
        assertThat(legs.benchmarkTotal().doubleValue()).isCloseTo(
                2 * 0.8801, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void skipsGamesThatCannotBeSettledOrPricedInTheTwoWayMarket() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, "A", "B", 1.80, 3.40, 2.20, "승", 1.80),   // pairs
                threeWay(2, "C", "D", 2.30, 3.30, 1.70, "패", 1.70),   // no 승패 listing
                threeWay(3, "E", "F", null, null, null, "승", 1.90));   // no published price
        List<BaseballResult> twoWayGames = List.of(twoWay(101, "A", "B", "승", 1.55));

        // Only one candidate survives, so no complete 2-leg slip can be formed.
        assertThat(algorithm.selectSlips(input(2, threeWayGames, twoWayGames))).isEmpty();
        assertThat(algorithm.selectSlips(input(1, threeWayGames, twoWayGames)))
                .singleElement()
                .satisfies(slip -> assertThat(slip.selections())
                        .extracting(PickSelection::resultId).containsExactly(101));
    }

    @Test
    void ordersLegsByTheEstimatedPriceAndChunksByCombinedN() {
        List<BaseballResult> threeWayGames = new ArrayList<>();
        List<BaseballResult> twoWayGames = new ArrayList<>();
        // Increasingly long favorites: 1.40 → 1.80 → 2.30 → 2.80 on the 3-way home side.
        double[] homePrices = {1.40, 1.80, 2.30, 2.80};
        for (int i = 0; i < homePrices.length; i++) {
            threeWayGames.add(threeWay(i + 1, "H" + i, "V" + i, homePrices[i], 3.40, 6.0, "승", 1.5));
            twoWayGames.add(twoWay(101 + i, "H" + i, "V" + i, "승", 1.5));
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
        assertThat(algorithm.paramSpace().specs()).extracting(ParamSpec::name)
                .containsExactlyInAnyOrder(
                        AlgorithmParams.COMBINED_N, FavoriteTwoWaySlipAlgorithm.ONE_RUN_HOME_SHARE);
    }
}
