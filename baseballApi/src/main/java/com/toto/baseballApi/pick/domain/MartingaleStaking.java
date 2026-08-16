package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Martingale staking state for one evaluation run (docs/martingale-staking-design.md §3).
 *
 * <p>Stake sizing is path-dependent — today's stake depends on yesterday's result — which is exactly
 * what {@link PickAlgorithm#selectSlips} is forbidden from seeing. The state therefore lives here,
 * outside the algorithm, and is folded over the days by the evaluator ({@code BacktestService} /
 * {@code PickSimulationService}) in ymd order.
 *
 * <p>State is keyed per {@code (year, round)} because one ymd can span two rounds and one round
 * spans many ymds; each round runs its own sequence against its own 50,000원 budget (R3/R5). When a
 * round's cumulative stakes would exceed the budget, the remainder is bet once as a final partial
 * bet and the round stops betting (R3). A hit resets the sequence to step one but keeps consuming
 * the same budget (R4).
 *
 * <p><b>One instance per run, never shared.</b> A parameter sweep evaluates candidates on parallel
 * threads; each candidate must create its own session via
 * {@link StakingAlgorithm#newStakingSession}, or the sequences bleed into each other.
 */
public final class MartingaleStaking {

    /** 한 라운드(회차)의 최대 누적매수금액 — 요구 조건이므로 스윕 대상이 아니다. */
    public static final BigDecimal MAX_ROUND_STAKE = BigDecimal.valueOf(50_000);

    /** 배팅금 올림 단위 (1000÷3 = 333.3 → 350원). */
    public static final BigDecimal STAKE_ROUNDING_UNIT = BigDecimal.valueOf(50);

    private final BigDecimal targetProfit;
    private final Map<RoundKey, RoundState> states = new HashMap<>();

    private record RoundKey(Integer year, Integer round) {
    }

    private static final class RoundState {
        private BigDecimal cumulativeLoss = BigDecimal.ZERO;
        private BigDecimal usedBudget = BigDecimal.ZERO;
    }

    public MartingaleStaking(BigDecimal targetProfit) {
        if (targetProfit == null || targetProfit.signum() <= 0) {
            throw new IllegalArgumentException("targetProfit must be > 0");
        }
        this.targetProfit = targetProfit;
    }

    /**
     * The stake for today's slip: recover this round's unrecovered losses and clear
     * {@code targetProfit}, rounded up to {@link #STAKE_ROUNDING_UNIT}, capped at what is left of
     * the round budget. {@code null} means "do not bet today": the odds cannot fund a recovery
     * (≤ 1, R7) or the budget is spent (R3). Skipped days leave the state untouched (R6).
     */
    public BigDecimal nextStake(Integer year, Integer round, BigDecimal combinedOdds) {
        if (combinedOdds == null || combinedOdds.compareTo(BigDecimal.ONE) <= 0) {
            return null;
        }
        RoundState state = states.computeIfAbsent(new RoundKey(year, round), k -> new RoundState());
        BigDecimal remaining = MAX_ROUND_STAKE.subtract(state.usedBudget);
        if (remaining.signum() <= 0) {
            return null;
        }
        BigDecimal needed = state.cumulativeLoss.add(targetProfit)
                .divide(combinedOdds.subtract(BigDecimal.ONE), 6, RoundingMode.HALF_UP)
                .divide(STAKE_ROUNDING_UNIT, 0, RoundingMode.CEILING)
                .multiply(STAKE_ROUNDING_UNIT);
        return needed.min(remaining);
    }

    /** Records the outcome of a stake handed out by {@link #nextStake} for the same round. */
    public void settle(Integer year, Integer round, BigDecimal stake, boolean hit) {
        RoundState state = states.get(new RoundKey(year, round));
        if (state == null) {
            throw new IllegalStateException("settle() without a prior nextStake() for round "
                    + year + "-" + round);
        }
        state.usedBudget = state.usedBudget.add(stake);
        state.cumulativeLoss = hit ? BigDecimal.ZERO : state.cumulativeLoss.add(stake);
    }
}
