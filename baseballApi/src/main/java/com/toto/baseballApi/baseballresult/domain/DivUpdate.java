package com.toto.baseballApi.baseballresult.domain;

/**
 * Per-outcome odds computed for one baseball_result row. homeDiv and awayDiv are
 * always non-null when an update is emitted; drawDiv may be null (estimate out of
 * plausible range).
 */
public record DivUpdate(
        Integer id,
        Double homeDiv,
        Double drawDiv,
        Double awayDiv) {
}
