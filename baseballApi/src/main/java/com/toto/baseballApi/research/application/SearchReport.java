package com.toto.baseballApi.research.application;

import java.util.List;

import com.toto.baseballApi.research.domain.BacktestWindow;
import com.toto.baseballApi.research.domain.Experiment;
import com.toto.baseballApi.research.domain.ExperimentGoal;

/**
 * The result of one sweep, shaped to answer the pipeline's last two stages in one payload:
 * what scored best (stage 3) and whether anything cleared the bar (stage 4).
 *
 * @param ranked           validated candidates, best first by validation score
 * @param bestPerAlgorithm one entry per algorithm — the family-level view, so a single algorithm
 *                         swept over hundreds of points cannot hide the others
 * @param achieved         candidates whose verdict passed; empty means keep iterating
 * @param diagnostics      why the leaders fell short, phrased as the next thing to try. This is
 *                         what an automated loop reads to choose its next hypothesis instead of
 *                         re-running the same sweep with a different seed
 */
public record SearchReport(
        BacktestWindow trainWindow,
        BacktestWindow validationWindow,
        ExperimentGoal goal,
        int candidateCount,
        int validatedCount,
        boolean goalAchieved,
        List<Experiment> ranked,
        List<Experiment> bestPerAlgorithm,
        List<Experiment> achieved,
        List<String> diagnostics) {
}
