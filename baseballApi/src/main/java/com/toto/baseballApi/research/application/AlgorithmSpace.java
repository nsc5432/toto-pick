package com.toto.baseballApi.research.application;

import java.util.List;

import com.toto.baseballApi.pick.domain.ParamSpec;

/**
 * What one registered algorithm exposes to a search — the catalogue an operator (or an agent
 * planning its next sweep) reads before deciding what to explore and how big the job will be.
 *
 * @param gridSize size of the exhaustive grid; compare against the per-algorithm budget to see
 *                 whether a sweep will enumerate it or fall back to random sampling
 */
public record AlgorithmSpace(
        String code, String name, boolean tunable, List<ParamSpec> params, long gridSize) {
}
