package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Martingale staking state for one evaluation run (docs/martingale-staking-design.md §3).
 *
 * <p>Stake sizing is path-dependent — today's stake depends on yesterday's result — which is exactly
 * what {@link PickAlgorithm#selectSlips} is forbidden from seeing. The state therefore lives here,
 * outside the algorithm, and is folded over the betting slots by the evaluator
 * ({@code BacktestService} / {@code PickSimulationService}) in chronological order.
 *
 * <p>One <b>sequence</b> runs from step one until it ends, and only two things end it: a hit (the
 * target profit is realized) or spending the {@link #MAX_SEQUENCE_STAKE} budget (the loss is taken).
 * Either way the next bet starts at step one with a full budget. Nothing else resets it — a 회차
 * boundary in particular does not, so an unrecovered loss keeps being carried until it is recovered
 * or written off. Keying the state per {@code (year, round)} is what used to make a round change
 * silently discard the running loss.
 *
 * <p><b>One instance per run, never shared.</b> A parameter sweep evaluates candidates on parallel
 * threads; each candidate must create its own session via
 * {@link StakingAlgorithm#newStakingSession}, or the sequences bleed into each other.
 */
public final class MartingaleStaking {

    /** 한 시퀀스(1단계 → 적중 또는 소진)의 최대 누적매수금액 — 요구 조건이므로 스윕 대상이 아니다. */
    public static final BigDecimal MAX_SEQUENCE_STAKE = BigDecimal.valueOf(50_000);

    /** 배팅금 올림 단위 (1000÷3 = 333.3 → 350원). */
    public static final BigDecimal STAKE_ROUNDING_UNIT = BigDecimal.valueOf(50);

    private final BigDecimal targetProfit;

    /** 현재 시퀀스에서 미회수된 손실 합. */
    private BigDecimal cumulativeLoss = BigDecimal.ZERO;

    /** 현재 시퀀스에서 지금까지 배팅한 금액 합. */
    private BigDecimal usedBudget = BigDecimal.ZERO;

    public MartingaleStaking(BigDecimal targetProfit) {
        if (targetProfit == null || targetProfit.signum() <= 0) {
            throw new IllegalArgumentException("targetProfit must be > 0");
        }
        this.targetProfit = targetProfit;
    }

    /**
     * The stake for this slot's slip: recover the sequence's unrecovered losses and clear
     * {@code targetProfit}, rounded up to {@link #STAKE_ROUNDING_UNIT}, capped at what is left of
     * the sequence budget (R3 — the capped bet is the sequence's last one). {@code null} means "do
     * not bet": the odds cannot fund a recovery (≤ 1, R7) or there are none (R6). Skipped slots
     * leave the state untouched.
     *
     * <p>Budget exhaustion is never a reason to return {@code null}: {@link #settle} restarts the
     * sequence when the budget runs out, so the remaining budget is always positive here.
     */
    public BigDecimal nextStake(BigDecimal combinedOdds) {
        if (combinedOdds == null || combinedOdds.compareTo(BigDecimal.ONE) <= 0) {
            return null;
        }
        BigDecimal needed = cumulativeLoss.add(targetProfit)
                .divide(combinedOdds.subtract(BigDecimal.ONE), 6, RoundingMode.HALF_UP)
                .divide(STAKE_ROUNDING_UNIT, 0, RoundingMode.CEILING)
                .multiply(STAKE_ROUNDING_UNIT);
        return needed.min(MAX_SEQUENCE_STAKE.subtract(usedBudget));
    }

    /** Records the outcome of a stake handed out by {@link #nextStake}. */
    public void settle(BigDecimal stake, boolean hit) {
        usedBudget = usedBudget.add(stake);
        if (hit || usedBudget.compareTo(MAX_SEQUENCE_STAKE) >= 0) {
            // 적중 → 목표수익 실현 / 소진 → 손실 확정. 어느 쪽이든 시퀀스가 끝나고 다음은 1단계다.
            cumulativeLoss = BigDecimal.ZERO;
            usedBudget = BigDecimal.ZERO;
        } else {
            cumulativeLoss = cumulativeLoss.add(stake);
        }
    }
}
