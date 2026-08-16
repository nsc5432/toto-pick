package com.toto.baseballApi.research.domain;

import java.util.List;

/**
 * Append-only store of every {@link Experiment} the pipeline has run — the pipeline's memory.
 *
 * <p>Append-only is deliberate. A search that overwrote its history would let the same dead end be
 * re-explored on every run and would quietly lose the evidence that an earlier candidate once
 * cleared the target. Because the ledger accumulates, a later session can ask what has already been
 * tried and pick up where the last one stopped instead of starting over.
 */
public interface ExperimentLedger {

    void appendAll(List<Experiment> experiments);

    /** Every recorded experiment, oldest first. */
    List<Experiment> findAll();

    /**
     * Best surviving experiment per algorithm code, ranked by {@link ObjectiveScore#BEST_FIRST} —
     * the leaderboard. Only one entry per algorithm, so a single algorithm swept over hundreds of
     * parameter points cannot crowd every other family off the board.
     */
    List<Experiment> leaderboard(int limit);
}
