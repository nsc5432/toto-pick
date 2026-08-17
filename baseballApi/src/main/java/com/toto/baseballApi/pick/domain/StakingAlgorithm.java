package com.toto.baseballApi.pick.domain;

/**
 * A {@link PickAlgorithm} that sizes its own stakes instead of taking the flat per-slip
 * {@code inputMoney}.
 *
 * <p>Selection stays stateless and slot-isolated as always; only the evaluator-side money fold
 * changes. An evaluator that sees this interface creates one session per run and routes the betting
 * slots — {@code (ymd, PickUniverse#combinationBucket)} pairs — through
 * {@link PickBacktester#runStakedSlot} in chronological order; every other algorithm keeps the flat
 * per-day path untouched.
 */
public interface StakingAlgorithm extends PickAlgorithm {

    /**
     * Fresh staking state for one evaluation run. Must be called once per run (per sweep
     * candidate) — sessions are stateful and must never be shared across runs or threads.
     */
    MartingaleStaking newStakingSession(AlgorithmParams params);
}
