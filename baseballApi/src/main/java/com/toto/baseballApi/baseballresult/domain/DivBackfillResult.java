package com.toto.baseballApi.baseballresult.domain;

/**
 * Outcome counts of one backfill run: {@code target} rows matched the missing-odds
 * filter, {@code updated} were written, {@code skipped} could not be computed
 * (no matching 승패 row, or a guard rejected the estimate).
 */
public record DivBackfillResult(
        int target,
        int updated,
        int skipped) {
}
