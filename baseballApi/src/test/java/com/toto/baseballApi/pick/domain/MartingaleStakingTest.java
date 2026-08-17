package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Rules R1–R7 of docs/martingale-staking-design.md, including its worked examples verbatim. */
class MartingaleStakingTest {

    private final MartingaleStaking staking = new MartingaleStaking(BigDecimal.valueOf(1000));

    private BigDecimal stake(double combinedOdds) {
        return staking.nextStake(BigDecimal.valueOf(combinedOdds));
    }

    private void miss(BigDecimal stake) {
        staking.settle(stake, false);
    }

    private void hit(BigDecimal stake) {
        staking.settle(stake, true);
    }

    @Test
    void designDocExample_firstBetAtOddsFour_is350() {
        // 1000 ÷ (4−1) = 333.3… → 50원 단위 올림 → 350원 (§2 정정 예시 1번).
        assertThat(stake(4.0)).isEqualByComparingTo("350");
    }

    @Test
    void designDocExample_secondBetAfterMissAtOddsThree_is700() {
        miss(stake(4.0)); // 350원 미적중
        // (350 + 1000) ÷ (3−1) = 675 → 50원 단위 올림 → 700원 (§2 정정 예시 2번).
        assertThat(stake(3.0)).isEqualByComparingTo("700");
    }

    @Test
    void workedExample_allMissesAtOddsThree_reachesTheCapAtStepTen() {
        // §4 표: 500, 750, 1150, 1700, 2550, 3850, 5750, 8650, 12950, 그리고 잔액 12,150원.
        String[] expected = {
                "500", "750", "1150", "1700", "2550", "3850", "5750", "8650", "12950", "12150" };
        for (String expectedStake : expected) {
            BigDecimal stake = stake(3.0);
            assertThat(stake).isEqualByComparingTo(expectedStake);
            miss(stake);
        }
        // 누적매수금액이 정확히 50,000원 — 시퀀스 손실이 확정되고 다음 배팅은 1단계로 재시작한다 (R3).
        assertThat(stake(3.0)).isEqualByComparingTo("500");
    }

    @Test
    void aHitEndsTheSequenceAndRestoresTheFullBudget() {
        miss(stake(3.0)); // 500원
        hit(stake(3.0)); // 750원 적중 → 시퀀스 종료 (R4)
        // 1단계부터 다시: 1000 ÷ 2 = 500원.
        assertThat(stake(3.0)).isEqualByComparingTo("500");

        // 예산도 함께 리셋된다 — 한도는 회차가 아니라 시퀀스 단위이므로 새 시퀀스는 50,000원을 온전히 쓴다.
        assertThat(staking.nextStake(BigDecimal.valueOf(1.01))).isEqualByComparingTo("50000");
    }

    @Test
    void anUnrecoveredLossCarriesUntilItIsRecovered_regardlessOfTheRound() {
        // 회귀: 회차가 바뀌면 누적손실을 버리던 버그. 상태에 회차 개념 자체가 없어야 한다.
        // 260421 2026-46회차 배당 2.80 → 1000 ÷ 1.80 = 555.6 → 600원, 미적중.
        BigDecimal first = stake(2.80);
        assertThat(first).isEqualByComparingTo("600");
        miss(first);

        // 260422 2026-46회차 배당 3.08 → (600 + 1000) ÷ 2.08 = 769.2 → 800원, 미적중.
        BigDecimal second = stake(3.08);
        assertThat(second).isEqualByComparingTo("800");
        miss(second);

        // 260422 2026-47회차 배당 2.82 — 회차가 바뀌어도 누적손실 1,400원을 그대로 이월한다:
        // (1400 + 1000) ÷ 1.82 = 1318.7 → 1,350원. (예전에는 550원으로 초기화됐다.)
        assertThat(stake(2.82)).isEqualByComparingTo("1350");
    }

    @Test
    void unusableOddsProduceNoBetAndLeaveTheStateUntouched() {
        assertThat(stake(1.0)).isNull(); // R7
        assertThat(staking.nextStake(null)).isNull(); // R6 (published odds 없음)
        // 상태가 그대로이므로 다음 배팅은 여전히 1단계 금액이다.
        assertThat(stake(3.0)).isEqualByComparingTo("500");
    }
}
