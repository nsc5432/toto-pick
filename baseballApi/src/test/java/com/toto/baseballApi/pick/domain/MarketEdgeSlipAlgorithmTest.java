package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class MarketEdgeSlipAlgorithmTest {

    private final MarketEdgeSlipAlgorithm algorithm = new MarketEdgeSlipAlgorithm();

    private int nextId = 1000;

    /** Ten games in which A beats B every time: A is on 100% form, B on 0%. */
    private List<BaseballResult> lopsidedHistory() {
        List<BaseballResult> games = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            games.add(new BaseballResult(nextId++, 2026, 76, "KBO",
                    String.format("2606%02d", day), "18:30", "A", "B",
                    "야구 승패", null, 0.0, null, "승", 1.5, null, null, null));
        }
        return games;
    }

    /**
     * A vs B priced 2.0 / 3.5 / 3.0. De-vigged that is home .4468, draw .2553, away .2979, so the
     * market gives the home side 60% of the decisive mass — the number the form estimate is
     * measured against below.
     */
    private BaseballResult pricedGame(Double pubHome, Double pubDraw, Double pubAway) {
        return new BaseballResult(1, 2026, 76, "KBO", "260630", "18:30", "A", "B",
                "야구 승1패", null, 0.0, null, "승", 2.0,
                pubHome, pubDraw, pubAway);
    }

    private SlipSelectionInput input(BaseballResult game, double edgeThreshold, double minOdds,
            double shrinkStrength) {
        Map<String, Double> params = new LinkedHashMap<>();
        params.put(AlgorithmParams.NUM, 10.0);
        params.put(AlgorithmParams.COMBINED_N, 1.0);
        params.put(MarketEdgeSlipAlgorithm.EDGE_THRESHOLD, edgeThreshold);
        params.put(MarketEdgeSlipAlgorithm.MIN_ODDS, minOdds);
        params.put(MarketEdgeSlipAlgorithm.SHRINK_STRENGTH, shrinkStrength);
        return new SlipSelectionInput("260630", 10, 1.8, 2.5, 1, List.of(game), lopsidedHistory())
                .withParams(AlgorithmParams.of(params));
    }

    private List<PickSelection> allSelections(List<PickSlip> slips) {
        return slips.stream().flatMap(s -> s.selections().stream()).toList();
    }

    @Test
    void formFarAboveTheMarketPriceIsBacked() {
        // shrink 25 → home share (100+25)/(100+0+50) = .8333; edge = .8333*.7447 − .4468 = +.174.
        List<PickSlip> slips = algorithm.selectSlips(input(pricedGame(2.0, 3.5, 3.0), 0.10, 1.5, 25));

        assertThat(allSelections(slips)).containsExactly(new PickSelection(1, "승"));
    }

    @Test
    void anEdgeBelowTheThresholdIsNotBacked() {
        assertThat(algorithm.selectSlips(input(pricedGame(2.0, 3.5, 3.0), 0.20, 1.5, 25))).isEmpty();
    }

    @Test
    void aPriceBelowMinOddsIsNotBacked() {
        assertThat(algorithm.selectSlips(input(pricedGame(2.0, 3.5, 3.0), 0.10, 2.5, 25))).isEmpty();
    }

    @Test
    void shrinkageCanPullTheEstimateAllTheWayOntoTheMarketPrice() {
        // shrink 200 → home share (100+200)/(100+400) = .60, exactly the market's decisive split,
        // so the edge collapses to 0 and even a hair-thin threshold rejects the game.
        assertThat(algorithm.selectSlips(input(pricedGame(2.0, 3.5, 3.0), 0.001, 1.5, 200)))
                .isEmpty();
    }

    @Test
    void gamesWithoutPublishedOddsAreSkipped() {
        assertThat(algorithm.selectSlips(input(pricedGame(null, null, null), 0.10, 1.5, 25)))
                .isEmpty();
    }

    @Test
    void everyThresholdTheAlgorithmUsesIsDeclaredAsASweptKnob() {
        // A threshold baked in as a constant is never swept, so it is never validated.
        assertThat(algorithm.paramSpace().specs()).extracting(ParamSpec::name)
                .contains(AlgorithmParams.NUM, AlgorithmParams.COMBINED_N,
                        MarketEdgeSlipAlgorithm.EDGE_THRESHOLD,
                        MarketEdgeSlipAlgorithm.MIN_ODDS,
                        MarketEdgeSlipAlgorithm.SHRINK_STRENGTH);
    }

    @Test
    void combinedNStaysWithinTheTwoToThreeRangeOfDesignDecisionD1() {
        ParamSpec combinedN = algorithm.paramSpace().specs().stream()
                .filter(spec -> AlgorithmParams.COMBINED_N.equals(spec.name()))
                .findFirst().orElseThrow();

        assertThat(combinedN.min()).isEqualTo(2);
        assertThat(combinedN.max()).isEqualTo(3);
    }
}
