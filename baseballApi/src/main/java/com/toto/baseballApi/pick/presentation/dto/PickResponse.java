package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

import com.toto.baseballApi.pick.domain.PickKpis;
import com.toto.baseballApi.pick.domain.PickMaster;

public record PickResponse(
        Integer id,
        Integer year,
        Integer round,
        String ymd,
        String userName,
        String algorithmCode,
        BigDecimal inputMoney,
        BigDecimal outputMoney,
        BigDecimal profitRate,
        List<PickDetailResponse> details) {

    public static PickResponse from(PickMaster pickMaster) {
        return new PickResponse(
                pickMaster.id(),
                pickMaster.year(),
                pickMaster.round(),
                pickMaster.ymd(),
                pickMaster.userName(),
                pickMaster.algorithmCode(),
                pickMaster.inputMoney(),
                pickMaster.outputMoney(),
                PickKpis.profitRate(pickMaster.inputMoney(), pickMaster.outputMoney()),
                pickMaster.details().stream().map(PickDetailResponse::from).toList());
    }
}
