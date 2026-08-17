package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class WinRateOddsTwoWaySlipSelectorTest {

    private static final int NUM = 10;
    /** Thresholds sit inside the 2-way price band (p05–p95 ≈ 1.38–2.19), not the 3-way one. */
    private static final double X = 1.40;
    private static final double Y = 1.80;

    /** 100% win rate — top-ranked. */
    private static final String STRONG = "T10";
    /** 90% — also top-ranked, which is how a contradiction is built. */
    private static final String STRONG2 = "T9";
    /** 50% — in neither set, so it never contributes a signal of its own. */
    private static final String MID = "T5";
    /** 0% — bottom-ranked. */
    private static final String WEAK = "T0";

    private final WinRateOddsTwoWaySlipSelector algorithm = new WinRateOddsTwoWaySlipSelector();

    private BaseballResult threeWay(int id, String home, String away,
            Double pubHome, Double pubDraw, Double pubAway) {
        return new BaseballResult(id, 2026, 76, "KBO", "260630", "18:30", home, away,
                "야구 승1패", null, 0.0, null, "승", 2.0,
                null, null, null, pubHome, pubDraw, pubAway);
    }

    private BaseballResult twoWay(int id, String home, String away) {
        return new BaseballResult(id, 2026, 76, "KBO", "260630", "18:30", home, away,
                "야구 승패", null, 0.0, null, "승", 1.6,
                null, null, null, null, null, null);
    }

    /**
     * Eleven teams {@code T0..T10} on win rates 0%, 10%, … 100%, each against its own sparring
     * partner {@code O0..O10} (which therefore takes the complementary rate).
     *
     * <p>Eleven distinct percentages matter: DENSE_RANK takes the top five and bottom five
     * <em>values</em>, so with fewer than ten distinct rates the two sets overlap and every team
     * would be simultaneously top and bottom — which makes every game contradictory and every test
     * vacuously empty.
     */
    private List<BaseballResult> gradedHistory() {
        List<BaseballResult> history = new ArrayList<>();
        int id = 900;
        for (int wins = 0; wins <= 10; wins++) {
            for (int game = 0; game < NUM; game++) {
                history.add(new BaseballResult(id++, 2026, 70, "KBO",
                        String.format("2606%02d", game + 1), "18:30", "T" + wins, "O" + wins,
                        "야구 승패", null, 0.0, null, game < wins ? "승" : "패", 1.5,
                        null, null, null, null, null, null));
            }
        }
        return history;
    }

    private SlipSelectionInput input(int combinedN,
            List<BaseballResult> threeWayGames, List<BaseballResult> twoWayGames) {
        List<BaseballResult> history = gradedHistory();
        return new SlipSelectionInput("260630", NUM, X, Y, combinedN,
                threeWayGames, history, TeamFormIndex.build(history),
                MarketPairIndex.build(twoWayGames), AlgorithmParams.empty());
    }

    private List<PickSelection> allSelections(List<PickSlip> slips) {
        return slips.stream().flatMap(s -> s.selections().stream()).toList();
    }

    @Test
    void rankingSeparatesTheTopAndBottomSets() {
        TeamRankSets ranks = TeamRankSets.of(TeamFormIndex.build(gradedHistory()), "260630", NUM, 5);

        assertThat(ranks.isTop(STRONG)).isTrue();
        assertThat(ranks.isBottom(STRONG)).isFalse();
        assertThat(ranks.isBottom(WEAK)).isTrue();
        assertThat(ranks.isTop(WEAK)).isFalse();
        assertThat(ranks.isTop(MID)).isFalse();
        assertThat(ranks.isBottom(MID)).isFalse();
    }

    @Test
    void aWiderRankLimitAdmitsMoreTeamsAndSoMoreCandidates() {
        TeamFormIndex form = TeamFormIndex.build(gradedHistory());

        // MID sits at 50% — outside the top/bottom five, inside a wider net. That is the whole
        // mechanism by which relaxing the filter buys sample size.
        assertThat(TeamRankSets.of(form, "260630", NUM, 5).isTop(MID)).isFalse();
        assertThat(TeamRankSets.of(form, "260630", NUM, 15).isTop(MID)).isTrue();
    }

    @Test
    void backsATopFormTeamThatIsStillPricedAboveX() {
        // 3-way 2.40/3.30/2.60 → 2-way home ≈ 1.62, comfortably above x = 1.40.
        List<BaseballResult> threeWayGames = List.of(threeWay(1, STRONG, MID, 2.40, 3.30, 2.60));
        List<BaseballResult> twoWayGames = List.of(twoWay(101, STRONG, MID));

        List<PickSelection> legs =
                allSelections(algorithm.selectSlips(input(1, threeWayGames, twoWayGames)));

        assertThat(legs).hasSize(1);
        // Points at the 승패 row, not the 승1패 row it read the signal from.
        assertThat(legs.get(0).resultId()).isEqualTo(101);
        assertThat(legs.get(0).predictedTotalResult()).isEqualTo("승");
        // Carries the estimated 2-way price, since the 승패 row has none of its own.
        assertThat(legs.get(0).price()).isNotNull();
        assertThat(legs.get(0).price().odds()).isBetween(1.4, 2.0);
    }

    @Test
    void fadesABottomFormTeamThatThePriceOvervalues() {
        // WEAK is short (3-way 1.50 → 2-way ≈ 1.21 ≤ y), so back its opponent.
        List<BaseballResult> threeWayGames = List.of(threeWay(1, WEAK, MID, 1.50, 3.60, 5.00));
        List<BaseballResult> twoWayGames = List.of(twoWay(101, WEAK, MID));

        List<PickSelection> legs =
                allSelections(algorithm.selectSlips(input(1, threeWayGames, twoWayGames)));

        assertThat(legs).hasSize(1);
        assertThat(legs.get(0).resultId()).isEqualTo(101);
        assertThat(legs.get(0).predictedTotalResult()).isEqualTo("패");
    }

    @Test
    void skipsATopFormTeamThatIsAlreadyTooShort() {
        // 3-way 1.40 → 2-way ≈ 1.16, under x — no value left in backing the strong side, and MID
        // is in neither set, so nothing else fires either.
        List<BaseballResult> threeWayGames = List.of(threeWay(1, STRONG, MID, 1.40, 3.80, 6.00));
        List<BaseballResult> twoWayGames = List.of(twoWay(101, STRONG, MID));

        assertThat(algorithm.selectSlips(input(1, threeWayGames, twoWayGames))).isEmpty();
    }

    @Test
    void skipsGamesWhereTheTwoTeamsSignalsContradict() {
        // Both teams are top-ranked and both sides clear x, so the rule wants to back both — which
        // is no information at all.
        List<BaseballResult> threeWayGames = List.of(threeWay(1, STRONG, STRONG2, 2.40, 3.30, 2.60));
        List<BaseballResult> twoWayGames = List.of(twoWay(101, STRONG, STRONG2));

        assertThat(algorithm.selectSlips(input(1, threeWayGames, twoWayGames))).isEmpty();
    }

    @Test
    void skipsGamesWithNoPublishedPriceOrNoTwoWayCounterpart() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, STRONG, MID, null, null, null),      // no published price
                threeWay(2, STRONG, MID, 2.40, 3.30, 2.60));     // no 승패 listing
        assertThat(algorithm.selectSlips(input(1, threeWayGames, List.of()))).isEmpty();
    }

    @Test
    void neverMixesMlbAndKboIntoOneSlip() {
        List<BaseballResult> threeWayGames = List.of(
                threeWay(1, STRONG, MID, 2.40, 3.30, 2.60),
                new BaseballResult(2, 2026, 76, "MLB", "260630", "08:30", STRONG, MID,
                        "야구 승1패", null, 0.0, null, "승", 2.0,
                        null, null, null, 2.40, 3.30, 2.60));
        List<BaseballResult> twoWayGames = List.of(
                twoWay(101, STRONG, MID),
                new BaseballResult(102, 2026, 76, "MLB", "260630", "08:30", STRONG, MID,
                        "야구 승패", null, 0.0, null, "승", 1.6,
                        null, null, null, null, null, null));

        // One candidate per bucket, so a 2-leg slip cannot be formed in either.
        assertThat(algorithm.selectSlips(input(2, threeWayGames, twoWayGames))).isEmpty();
        assertThat(algorithm.selectSlips(input(1, threeWayGames, twoWayGames))).hasSize(2);
    }

    @Test
    void declaresEveryThresholdItUses() {
        assertThat(algorithm.paramSpace().specs()).extracting(ParamSpec::name)
                .containsExactlyInAnyOrder(
                        AlgorithmParams.NUM, AlgorithmParams.X, AlgorithmParams.Y,
                        AlgorithmParams.COMBINED_N, TeamRankSets.RANK_LIMIT,
                        FavoriteTwoWaySlipAlgorithm.ONE_RUN_HOME_SHARE);
    }

    @Test
    void thresholdsSpanTheTwoWayPriceBandRatherThanTheThreeWayOne() {
        // A threshold range that falls outside the data never binds. 2-way prices run 1.38–2.19
        // between the 5th and 95th percentiles; the 3-way range (1.4–3.0) would sit almost entirely
        // above them.
        ParamSpec x = algorithm.paramSpace().specs().stream()
                .filter(s -> s.name().equals(AlgorithmParams.X)).findFirst().orElseThrow();

        assertThat(x.min()).isLessThan(1.38);
        assertThat(x.max()).isGreaterThan(2.19);
    }
}
