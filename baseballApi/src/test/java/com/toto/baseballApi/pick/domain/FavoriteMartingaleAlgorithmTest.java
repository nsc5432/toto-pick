package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

class FavoriteMartingaleAlgorithmTest {

    private final FavoriteMartingaleAlgorithm algorithm = new FavoriteMartingaleAlgorithm();

    /** 3-way game: published prices set the favorite; totalResult/totalDiv settle it. */
    private BaseballResult game(int id, String ymd, String home, String away,
            double pubHome, double pubDraw, double pubAway, String totalResult, Double totalDiv) {
        return new BaseballResult(id, 2026, 76, "KBO", ymd, "18:30", home, away,
                "야구 승1패", null, 0.0, null, totalResult, totalDiv,
                pubHome, pubDraw, pubAway);
    }

    private SlipSelectionInput input(String ymd, List<BaseballResult> dayGames) {
        // combinedN deliberately 3: the algorithm must pin itself to 2 regardless of the request.
        return new SlipSelectionInput(ymd, 10, 1.8, 2.5, 3, dayGames, List.of());
    }

    @Test
    void picksExactlyOneSlipOfTheTwoLowestOddsFavorites() {
        List<BaseballResult> games = List.of(
                game(1, "260630", "A", "B", 1.5, 4.0, 5.0, "승", 1.5),
                game(2, "260630", "C", "D", 1.8, 3.8, 3.6, "승", 1.8),
                game(3, "260630", "E", "F", 3.4, 3.6, 2.0, "패", 2.0),
                game(4, "260630", "G", "H", 2.5, 3.4, 2.8, "승", 2.5));

        List<PickSlip> slips = algorithm.selectSlips(input("260630", games));

        // 4개 후보 중 최저 배당 2개(1.5, 1.8)만 — 슬롯당 1슬립, 2게임 조합 강제 (R2).
        assertThat(slips).hasSize(1);
        assertThat(slips.get(0).selections()).containsExactly(
                new PickSelection(1, "승"), new PickSelection(2, "승"));
    }

    @Test
    void stakingSessionReadsTargetProfitFromParams() {
        MartingaleStaking session = algorithm.newStakingSession(
                AlgorithmParams.empty().with(FavoriteMartingaleAlgorithm.TARGET_PROFIT, 2000));
        // 2000 ÷ (4−1) = 666.7 → 700원.
        assertThat(session.nextStake(BigDecimal.valueOf(4.0))).isEqualByComparingTo("700");
    }

    @Test
    void runStakedSlotFoldsMissesIntoTheNextSlotsStake() {
        MartingaleStaking staking = algorithm.newStakingSession(AlgorithmParams.empty());

        // Day 1: favorites at 1.5/1.8 → combined 2.70; stake 1000÷1.7 = 588.2 → 600원. 홈팀 하나가
        // 지면서 슬립 미적중.
        List<BaseballResult> day1 = List.of(
                game(1, "260701", "A", "B", 1.5, 4.0, 5.0, "패", 5.0),
                game(2, "260701", "C", "D", 1.8, 3.8, 3.6, "승", 1.8));
        List<SettledSlip> settled1 = PickBacktester.runStakedSlot(
                algorithm, input("260701", day1), byId(day1), staking);

        assertThat(settled1).hasSize(1);
        assertThat(settled1.get(0).inputMoney()).isEqualByComparingTo("600");
        assertThat(settled1.get(0).hit()).isFalse();

        // Day 2: 같은 가격대 → stake (600+1000)÷1.7 = 941.2 → 950원. 적중 시 회수
        // 2.70 × 950 = 2,565원 — 누적투입 1,550원을 회수하고 목표수익 1,000원 이상을 남긴다 (R1).
        List<BaseballResult> day2 = List.of(
                game(3, "260702", "E", "F", 1.5, 4.0, 5.0, "승", 1.5),
                game(4, "260702", "G", "H", 1.8, 3.8, 3.6, "승", 1.8));
        List<SettledSlip> settled2 = PickBacktester.runStakedSlot(
                algorithm, input("260702", day2), byId(day2), staking);

        assertThat(settled2).hasSize(1);
        assertThat(settled2.get(0).inputMoney()).isEqualByComparingTo("950");
        assertThat(settled2.get(0).outputMoney()).isEqualByComparingTo("2565");
        BigDecimal profit = settled2.get(0).outputMoney()
                .subtract(settled1.get(0).inputMoney())
                .subtract(settled2.get(0).inputMoney());
        assertThat(profit).isGreaterThanOrEqualTo(BigDecimal.valueOf(1000));
    }

    private Map<Integer, BaseballResult> byId(List<BaseballResult> games) {
        return games.stream().collect(Collectors.toMap(BaseballResult::id, Function.identity()));
    }
}
