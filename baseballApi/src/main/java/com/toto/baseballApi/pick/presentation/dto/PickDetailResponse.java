package com.toto.baseballApi.pick.presentation.dto;

import com.toto.baseballApi.pick.domain.PickDetail;

public record PickDetailResponse(
        Integer resultId,
        String totalResult) {

    public static PickDetailResponse from(PickDetail pickDetail) {
        return new PickDetailResponse(pickDetail.resultId(), pickDetail.totalResult());
    }
}
