package com.toto.baseballApi.research.application;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.toto.baseballApi.research.domain.ExperimentGoal;

/**
 * The pipeline's declared target and search budget, bound from {@code research.*} in
 * {@code application.yaml}.
 *
 * <p>Keeping the target in configuration rather than in code is what makes "did we hit the goal?" a
 * question with an answer: it is written down once, before any search runs, and every experiment in
 * the ledger is judged against it. Every field falls back to a working default when absent, so the
 * pipeline runs out of the box and the yaml only needs the values being changed.
 */
@ConfigurationProperties("research")
public record ResearchProperties(Goal goal, Search search, Ledger ledger) {

    public ResearchProperties {
        goal = goal == null ? new Goal(null, null, null, null, null) : goal;
        search = search == null ? new Search(null, null, null, null) : search;
        ledger = ledger == null ? new Ledger(null) : ledger;
    }

    /**
     * The target a candidate must clear on the validation window.
     *
     * @param minHitRate  optional; skipped when absent
     * @param maxDrawdown optional; skipped when absent
     */
    public record Goal(
            BigDecimal targetProfitRate,
            Integer minSlipCount,
            Integer minBettingDayCount,
            BigDecimal minHitRate,
            BigDecimal maxDrawdown) {

        public Goal {
            targetProfitRate = targetProfitRate == null ? new BigDecimal("0.10") : targetProfitRate;
            minSlipCount = minSlipCount == null ? 100 : minSlipCount;
            minBettingDayCount = minBettingDayCount == null ? 20 : minBettingDayCount;
        }

        public ExperimentGoal toDomain() {
            return new ExperimentGoal(
                    targetProfitRate, minSlipCount, minBettingDayCount, minHitRate, maxDrawdown);
        }
    }

    /**
     * How wide a sweep runs.
     *
     * @param trainRatio               share of days used to pick parameters; the rest validate
     * @param maxCandidatesPerAlgorithm budget per algorithm — the exhaustive grid is used while it
     *                                  fits, and a seeded random sample of this size beyond that
     * @param validationTopK           how many train-window leaders are re-scored on validation.
     *                                 Scoring every candidate on validation would turn the held-out
     *                                 window into a second training set, one selection at a time
     * @param seed                     fixed so a recorded sweep can be re-run and reproduced
     */
    public record Search(
            Double trainRatio, Integer maxCandidatesPerAlgorithm, Integer validationTopK, Long seed) {

        public Search {
            trainRatio = trainRatio == null ? 0.7 : trainRatio;
            maxCandidatesPerAlgorithm = maxCandidatesPerAlgorithm == null ? 200 : maxCandidatesPerAlgorithm;
            validationTopK = validationTopK == null ? 5 : validationTopK;
            seed = seed == null ? 42L : seed;
        }
    }

    /** @param path JSONL ledger file, relative to the API process's working directory */
    public record Ledger(String path) {

        public Ledger {
            path = path == null || path.isBlank() ? "research/experiments.jsonl" : path;
        }
    }
}
