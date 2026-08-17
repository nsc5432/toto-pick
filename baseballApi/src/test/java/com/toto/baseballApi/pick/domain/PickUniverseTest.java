package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class PickUniverseTest {

    private BaseballResult game(int id, int round, String tm, String home, String away, Double pubHome) {
        return new BaseballResult(id, 2026, round, "MLB", "260422", tm, home, away,
                "야구 승1패", null, 0.0, null, "승", 1.7,
                pubHome, 3.3, 1.7);
    }

    @Test
    void kboAndNpbShareABucketBecauseTheyShareATimeSlot() {
        assertThat(PickUniverse.combinationBucket("KBO"))
                .isEqualTo(PickUniverse.combinationBucket("NPB"));
    }

    @Test
    void mlbIsItsOwnBucket() {
        assertThat(PickUniverse.combinationBucket("MLB"))
                .isNotEqualTo(PickUniverse.combinationBucket("KBO"));
    }

    @Test
    void everyEligibleTournamentFallsIntoExactlyOneOfTheTwoBuckets() {
        assertThat(PickUniverse.TOURNAMENTS)
                .allSatisfy(tournament -> assertThat(PickUniverse.combinationBucket(tournament))
                        .isIn("MLB", "KBO_NPB"));
    }

    @Test
    void theSameGameSoldInTwoRoundsCollapsesToTheEarlierListing() {
        // 실제 데이터: 260422 10:45 샌프란시스코 vs LA다저스가 46회차(id 11120)와 47회차(id 10721)에
        // 각각 다른 가격으로 올라와 있다. 슬롯이 회차를 가로지르므로 하나로 접어야 한다.
        List<BaseballResult> distinct = PickUniverse.distinctFixtures(List.of(
                game(10721, 47, "10:45", "샌프란시스코자이언츠", "LA다저스", 3.75),
                game(11120, 46, "10:45", "샌프란시스코자이언츠", "LA다저스", 3.65)));

        assertThat(distinct).extracting(BaseballResult::id).containsExactly(11120);
    }

    @Test
    void aDoubleheaderStaysTwoGames() {
        // 같은 팀이 같은 날 두 번 붙는 경우는 경기 시각이 다르므로 중복이 아니다.
        List<BaseballResult> distinct = PickUniverse.distinctFixtures(List.of(
                game(1, 46, "10:45", "샌프란시스코자이언츠", "LA다저스", 3.75),
                game(2, 46, "14:10", "샌프란시스코자이언츠", "LA다저스", 3.65)));

        assertThat(distinct).extracting(BaseballResult::id).containsExactly(1, 2);
    }

    @Test
    void aListingWithoutPublishedOddsLosesToOneThatHasThem() {
        List<BaseballResult> distinct = PickUniverse.distinctFixtures(List.of(
                game(5, 46, "10:45", "샌프란시스코자이언츠", "LA다저스", null),
                game(9, 47, "10:45", "샌프란시스코자이언츠", "LA다저스", 3.65)));

        assertThat(distinct).extracting(BaseballResult::id).containsExactly(9);
    }
}
