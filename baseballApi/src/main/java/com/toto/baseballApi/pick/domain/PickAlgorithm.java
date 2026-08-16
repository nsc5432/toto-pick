package com.toto.baseballApi.pick.domain;

import java.util.List;

/**
 * Port for a pluggable slip-selection strategy (DIP boundary). Every implementation carries a
 * stable {@link #code()} that is persisted to {@code pick_mstr.algorithm_code}, so KPIs can be
 * aggregated per algorithm afterwards. Pure-logic implementations live in this package and are
 * registered via {@code PickAlgorithmConfig}; data-dependent ones live in
 * {@code infrastructure.algorithm} as components. Registering a bean with a unique code is all
 * it takes for an algorithm to appear in {@code GET /api/picks/algorithms}, the simulation, and
 * the comparison dashboard.
 */
public interface PickAlgorithm {

    /** Stable identity persisted to {@code pick_mstr.algorithm_code}, e.g. {@code "WIN_RATE_ODDS"}. */
    String code();

    /** Human-readable display name for the UI, e.g. {@code "승률-배당 괴리"}. */
    String name();

    List<PickSlip> selectSlips(SlipSelectionInput input);
}
