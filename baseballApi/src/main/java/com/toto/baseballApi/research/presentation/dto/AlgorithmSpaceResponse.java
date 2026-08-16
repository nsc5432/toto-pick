package com.toto.baseballApi.research.presentation.dto;

import java.util.List;

import com.toto.baseballApi.research.application.AlgorithmSpace;

/** One algorithm's tunable surface — what a sweep can vary, and how large the full grid is. */
public record AlgorithmSpaceResponse(
        String code, String name, boolean tunable, List<ParamResponse> params, long gridSize) {

    public record ParamResponse(
            String name, double min, double max, double step, double defaultValue, int cardinality) {
    }

    public static AlgorithmSpaceResponse from(AlgorithmSpace space) {
        return new AlgorithmSpaceResponse(
                space.code(), space.name(), space.tunable(),
                space.params().stream()
                        .map(spec -> new ParamResponse(
                                spec.name(), spec.min(), spec.max(), spec.step(),
                                spec.defaultValue(), spec.cardinality()))
                        .toList(),
                space.gridSize());
    }
}
