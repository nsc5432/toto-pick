package com.toto.baseballApi.research.presentation.dto;

import java.util.List;

import com.toto.baseballApi.research.application.SearchReport;

import com.toto.baseballApi.research.presentation.dto.ExperimentResponse.WindowResponse;

/**
 * A sweep's answer to both closing stages of the pipeline in one payload: what scored best, and
 * whether anything cleared the bar. {@code diagnostics} carries the "what next" — the reason the
 * leaders fell short, phrased as the change worth trying.
 */
public record SearchResponse(
        WindowResponse trainWindow,
        WindowResponse validationWindow,
        GoalResponse goal,
        int candidateCount,
        int validatedCount,
        boolean goalAchieved,
        List<ExperimentResponse> bestPerAlgorithm,
        List<ExperimentResponse> ranked,
        List<ExperimentResponse> achieved,
        List<String> diagnostics) {

    public static SearchResponse from(SearchReport report) {
        return new SearchResponse(
                WindowResponse.from(report.trainWindow()),
                WindowResponse.from(report.validationWindow()),
                GoalResponse.from(report.goal()),
                report.candidateCount(),
                report.validatedCount(),
                report.goalAchieved(),
                report.bestPerAlgorithm().stream().map(ExperimentResponse::from).toList(),
                report.ranked().stream().map(ExperimentResponse::from).toList(),
                report.achieved().stream().map(ExperimentResponse::from).toList(),
                report.diagnostics());
    }
}
