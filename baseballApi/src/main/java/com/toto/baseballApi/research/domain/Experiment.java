package com.toto.baseballApi.research.domain;

import java.util.Map;

/**
 * One immutable, fully-reproducible record of "this algorithm, with these parameters, scored this".
 *
 * <p>This is the artifact the whole pipeline exists to produce. The simulation tables cannot serve
 * the purpose: {@code pick_mstr} is keyed on {@code algorithm_code} alone, so re-running the same
 * algorithm at a different {@code x} overwrites the previous run and the comparison is gone. An
 * experiment instead carries its parameters, both windows, both windows' metrics, and the verdict,
 * so a search's history accumulates rather than erasing itself.
 *
 * <p>Everything needed to re-run it is on the record — the windows, {@code params},
 * {@code inputMoney} and {@code seed} — which is what makes a ledger entry checkable rather than
 * merely claimed.
 *
 * @param trainMetrics      metrics on the window the parameters were selected on; shown only to be
 *                          compared against validation, never quoted as the result
 * @param validationMetrics metrics on held-out days — the honest number, and the one
 *                          {@code verdict} and {@code score} are computed from
 */
public record Experiment(
        String id,
        String createdAt,
        String algorithmCode,
        String algorithmName,
        Map<String, Double> params,
        String paramSignature,
        BacktestWindow trainWindow,
        BacktestWindow validationWindow,
        BacktestMetrics trainMetrics,
        BacktestMetrics validationMetrics,
        ObjectiveScore score,
        GoalVerdict verdict,
        String inputMoney,
        long seed,
        String note) {
}
