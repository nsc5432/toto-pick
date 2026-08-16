package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class TeamFormIndexTest {

    private int nextId = 1;

    private BaseballResult twoWay(String ymd, String home, String away, String totalResult) {
        return new BaseballResult(nextId++, 2026, 76, "KBO", ymd, "18:30", home, away,
                "야구 승패", null, 0.0, null, totalResult, 1.5, null, null, null);
    }

    @Test
    void winPercentCountsOnlyGamesStrictlyBeforeTheGivenDate() {
        // A wins every game up to the 10th, then loses on the 11th. Asked as of the 11th, the loss
        // is that day's own result — the thing a backtest must not be able to see.
        List<BaseballResult> games = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            games.add(twoWay(String.format("2606%02d", day), "A", "B", "승"));
        }
        games.add(twoWay("260611", "A", "B", "패"));

        TeamFormIndex index = TeamFormIndex.build(games);

        assertThat(index.winPercent("A", "260611", 20)).isEqualTo(100L);
        assertThat(index.winPercent("A", "260612", 20)).isEqualTo(91L);
    }

    @Test
    void onlyTheMostRecentNumAppearancesCount() {
        List<BaseballResult> games = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            games.add(twoWay(String.format("2605%02d", day), "A", "B", "패"));
        }
        for (int day = 1; day <= 10; day++) {
            games.add(twoWay(String.format("2606%02d", day), "A", "B", "승"));
        }

        TeamFormIndex index = TeamFormIndex.build(games);

        assertThat(index.winPercent("A", "260701", 10)).isEqualTo(100L);
        assertThat(index.winPercent("A", "260701", 20)).isEqualTo(50L);
    }

    @Test
    void awayResultsAreReadFromTheAwayTeamsPointOfView() {
        TeamFormIndex index = TeamFormIndex.build(List.of(twoWay("260601", "A", "B", "패")));

        assertThat(index.winPercent("A", "260602", 5)).isEqualTo(0L);
        assertThat(index.winPercent("B", "260602", 5)).isEqualTo(100L);
    }

    @Test
    void aTeamWithNoPriorGamesHasNoFormRatherThanZeroPercent() {
        TeamFormIndex index = TeamFormIndex.build(List.of(twoWay("260610", "A", "B", "승")));

        assertThat(index.winPercent("A", "260610", 5)).isNull();
        assertThat(index.winPercent("UNKNOWN", "260701", 5)).isNull();
    }

    @Test
    void winPercentsOmitsTeamsWithoutFormSoTheyAreNotRanked() {
        List<BaseballResult> games = List.of(
                twoWay("260601", "A", "B", "승"),
                twoWay("260610", "C", "D", "승"));

        assertThat(TeamFormIndex.build(games).winPercents("260605", 5)).containsOnlyKeys("A", "B");
    }

    @Test
    void anEmptyIndexAnswersNothingRatherThanFailing() {
        assertThat(TeamFormIndex.empty().winPercent("A", "260601", 5)).isNull();
        assertThat(TeamFormIndex.empty().winPercents("260601", 5)).isEmpty();
    }
}
