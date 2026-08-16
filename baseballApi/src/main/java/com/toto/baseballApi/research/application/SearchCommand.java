package com.toto.baseballApi.research.application;

import java.util.List;

import com.toto.baseballApi.research.domain.ExperimentGoal;

/**
 * One sweep request. Every field except the date range is optional at the API edge and filled in
 * from {@code research.*} configuration, so the common case is "search this range" and a deliberate
 * deviation from the standing budget or target has to be spelled out.
 *
 * @param algorithmCodes null/empty means every registered algorithm
 * @param goal           null means the configured standing target — override only to explore what a
 *                       different bar would have accepted, never to lower it after seeing results
 */
public record SearchCommand(
        String bgngYmd,
        String endYmd,
        double trainRatio,
        BacktestSettings settings,
        List<String> algorithmCodes,
        int maxCandidatesPerAlgorithm,
        int validationTopK,
        long seed,
        ExperimentGoal goal,
        String note) {
}
