package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Rules R1–R7 of docs/martingale-staking-design.md, including its worked examples verbatim. */
class MartingaleStakingTest {

    private static final Integer YEAR = 2026;
    private static final Integer ROUND = 76;

    private final MartingaleStaking staking = new MartingaleStaking(BigDecimal.valueOf(1000));

    private BigDecimal stake(double combinedOdds) {
        return staking.nextStake(YEAR, ROUND, BigDecimal.valueOf(combinedOdds));
    }

    private void miss(BigDecimal stake) {
        staking.settle(YEAR, ROUND, stake, false);
    }

    private void hit(BigDecimal stake) {
        staking.settle(YEAR, ROUND, stake, true);
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
        // 누적매수금액이 정확히 50,000원 — 이후 이 라운드는 배팅하지 않는다 (R3).
        assertThat(stake(3.0)).isNull();
    }

    @Test
    void aHitResetsTheSequenceButKeepsConsumingTheRoundBudget() {
        miss(stake(3.0)); // 500원
        hit(stake(3.0)); // 750원 적중 → 리셋 (R4)
        // 1단계부터 다시: 1000 ÷ 2 = 500원.
        assertThat(stake(3.0)).isEqualByComparingTo("500");

        // 하지만 한도는 계속 누적: 이미 1,250원을 썼으므로 잔여는 48,750원이다.
        BigDecimal huge = staking.nextStake(YEAR, ROUND, BigDecimal.valueOf(1.01));
        assertThat(huge).isEqualByComparingTo("48750");
    }

    @Test
    void roundsRunIndependentBudgetsAndSequences() {
        miss(stake(3.0)); // round 76에서 500원 미적중
        // 다른 라운드는 처음부터: 누적손실 0 → 500원 (R5).
        assertThat(staking.nextStake(YEAR, 77, BigDecimal.valueOf(3.0))).isEqualByComparingTo("500");
    }

    @Test
    void unusableOddsProduceNoBetAndLeaveTheStateUntouched() {
        assertThat(stake(1.0)).isNull(); // R7
        assertThat(staking.nextStake(YEAR, ROUND, null)).isNull(); // R6 (published odds 없음)
        // 상태가 그대로이므로 다음 배팅은 여전히 1단계 금액이다.
        assertThat(stake(3.0)).isEqualByComparingTo("500");
    }

    @Test
    void settlingWithoutAHandedOutStakeIsABug() {
        assertThatIllegalStateException()
                .isThrownBy(() -> staking.settle(YEAR, ROUND, BigDecimal.valueOf(500), false));
    }
}
