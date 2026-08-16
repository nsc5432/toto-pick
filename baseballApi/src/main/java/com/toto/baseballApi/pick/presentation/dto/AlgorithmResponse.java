package com.toto.baseballApi.pick.presentation.dto;

import com.toto.baseballApi.pick.application.AlgorithmInfo;

public record AlgorithmResponse(String code, String name) {

    public static AlgorithmResponse from(AlgorithmInfo info) {
        return new AlgorithmResponse(info.code(), info.name());
    }
}
