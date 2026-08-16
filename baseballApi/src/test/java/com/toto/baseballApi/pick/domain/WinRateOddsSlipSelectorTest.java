package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class WinRateOddsSlipSelectorTest {

    private static final int NUM = 10;
    private static final double X = 2.5;
    private static final double Y = 2.1;

    private final WinRateOddsSlipSelector selector = new WinRateOddsSlipSelector();

    private int nextId = 1000;

    /**
     * Six fixed pairs, ten games each: W1..W6 win 10/9/8/7/6/5 of them, so the win
     * percentages are 100,90,80,70,60,50 for W-teams and 0,10,20,30,40,50 for L-teams.
     * Dense-rank top-5 = W1..W5, bottom-5 = L1..L5; W6/L6 (50%) are in neither set.
     */
    private List<BaseballResult> pairHistory() {
        List<BaseballResult> games = new ArrayList<>();
        int[] winnerWins = { 10, 9, 8, 7, 6, 5 };
        for (int p = 0; p < winnerWins.length; p++) {
            for (int g = 0; g < 10; g++) {
                games.add(twoWay(String.format("2606%02d", g + 1),
                        "W" + (p + 1), "L" + (p + 1), g < winnerWins[p] ? "승" : "패"));
            }
        }
        return games;
    }

    private BaseballResult twoWay(String ymd, String home, String away, String totalResult) {
        return new BaseballResult(nextId++, 2026, 76, "KBO", ymd, "18:30", home, away,
                "야구 승패", null, 0.0, null, totalResult, 1.5, null, null, null);
    }

    private BaseballResult threeWay(int id, String tournament, String home, String away,
            Double homeDiv, Double awayDiv) {
        return new BaseballResult(id, 2026, 76, tournament, "260630", "18:30", home, away,
                "야구 승1패", null, 0.0, null, "승", 2.0, homeDiv, 5.0, awayDiv);
    }

    private SlipSelectionInput input(int combinedN, List<BaseballResult> history,
            BaseballResult... dayGames) {
        return new SlipSelectionInput("260630", NUM, X, Y, combinedN, List.of(dayGames), history);
    }

    private List<PickSelection> allSelections(List<PickSlip> slips) {
        return slips.stream().flatMap(s -> s.selections().stream()).toList();
    }

    @Test
    void topHomeTeamWithOddsAtLeastXIsPickedToWin() {
        List<PickSlip> slips = selector.selectSlips(input(1, pairHistory(),
                threeWay(1, "KBO", "W1", "W6", 2.5, 2.8)));

        assertThat(allSelections(slips)).containsExactly(new PickSelection(1, "승"));
    }

    @Test
    void topAwayTeamWithOddsAtLeastXIsPickedToWin() {
        List<PickSlip> slips = selector.selectSlips(input(1, pairHistory(),
                threeWay(2, "KBO", "W6", "W2", 1.5, 2.6)));

        assertThat(allSelections(slips)).containsExactly(new PickSelection(2, "패"));
    }

    @Test
    void bottomHomeTeamWithOddsAtMostYIsFadedToLose() {
        List<PickSlip> slips = selector.selectSlips(input(1, pairHistory(),
                threeWay(3, "KBO", "L1", "W6", 1.8, 3.5)));

        assertThat(allSelections(slips)).containsExactly(new PickSelection(3, "패"));
    }

    @Test
    void bottomAwayTeamWithOddsAtMostYIsFadedToLose() {
        List<PickSlip> slips = selector.selectSlips(input(1, pairHistory(),
                threeWay(4, "KBO", "W6", "L2", 3.5, 2.0)));

        assertThat(allSelections(slips)).containsExactly(new PickSelection(4, "승"));
    }

    @Test
    void teamsOutsideBothRankSetsProduceNoPick() {
        List<PickSlip> slips = selector.selectSlips(input(1, pairHistory(),
                threeWay(5, "KBO", "W6", "L6", 1.8, 2.6)));

        assertThat(slips).isEmpty();
    }

    @Test
    void contradictorySignalsSkipTheGame() {
        // Home L1 (bottom, low odds → "패") vs away L2 (bottom, low odds → "승") — contradiction.
        List<PickSlip> slips = selector.selectSlips(input(1, pairHistory(),
                threeWay(6, "KBO", "L1", "L2", 1.8, 2.0)));

        assertThat(slips).isEmpty();
    }

    @Test
    void nullOddsOnTheSignalingTeamAreSkipped() {
        List<PickSlip> slips = selector.selectSlips(input(1, pairHistory(),
                threeWay(7, "KBO", "W1", "W6", null, 2.8)));

        assertThat(slips).isEmpty();
    }

    @Test
    void winRateWindowOnlyCountsTheMostRecentNumGames() {
        // 15 extra older games where W1 loses to L1: inside the NUM=10 window W1 is
        // still 100% and L1 still 0%, so both signals must be unaffected.
        List<BaseballResult> history = pairHistory();
        for (int g = 0; g < 15; g++) {
            history.add(twoWay(String.format("2605%02d", g + 1), "W1", "L1", "패"));
        }

        List<PickSlip> slips = selector.selectSlips(input(1, history,
                threeWay(8, "KBO", "W1", "W6", 2.5, 2.8),
                threeWay(9, "KBO", "L1", "W6", 1.8, 3.5)));

        assertThat(allSelections(slips)).containsExactlyInAnyOrder(
                new PickSelection(8, "승"), new PickSelection(9, "패"));
    }

    @Test
    void candidatesAreChunkedByLowestOddsAndTheRemainderIsDropped() {
        // Three candidates, combinedN=2 → one slip of the two lowest odds (2.5, 2.7);
        // the highest-odds candidate (3.0) is dropped.
        List<PickSlip> slips = selector.selectSlips(input(2, pairHistory(),
                threeWay(10, "KBO", "W1", "W6", 2.5, 2.8),
                threeWay(11, "KBO", "W2", "L6", 3.0, 2.9),
                threeWay(12, "NPB", "W3", "W6", 2.7, 2.8)));

        assertThat(slips).hasSize(1);
        assertThat(slips.get(0).selections()).containsExactly(
                new PickSelection(10, "승"), new PickSelection(12, "승"));
    }

    @Test
    void mlbIsCombinedSeparatelyFromKboAndNpb() {
        // KBO+NPB share one group (a full slip); the lone MLB candidate cannot fill
        // a slip of 2 on its own and is dropped rather than mixed in.
        List<PickSlip> slips = selector.selectSlips(input(2, pairHistory(),
                threeWay(13, "KBO", "W1", "W6", 2.5, 2.8),
                threeWay(14, "NPB", "W2", "W6", 2.6, 2.8),
                threeWay(15, "MLB", "W3", "W6", 2.5, 2.8)));

        assertThat(slips).hasSize(1);
        assertThat(slips.get(0).selections()).containsExactly(
                new PickSelection(13, "승"), new PickSelection(14, "승"));
    }
}
