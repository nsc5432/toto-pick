package com.toto.baseballApi.baseballresult.presentation.dto;

import com.toto.baseballApi.baseballresult.domain.DivBackfillResult;

public record DivBackfillResponse(
        int target,
        int updated,
        int skipped) {

    public static DivBackfillResponse from(DivBackfillResult result) {
        return new DivBackfillResponse(
                result.target(),
                result.updated(),
                result.skipped());
    }
}
