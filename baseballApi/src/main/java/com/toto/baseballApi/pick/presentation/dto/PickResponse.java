package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.toto.baseballApi.pick.domain.PickMaster;

public record PickResponse(
        Integer id,
        Integer year,
        Integer round,
        String ymd,
        String userName,
        BigDecimal inputMoney,
        BigDecimal outputMoney,
        BigDecimal profitRate,
        List<PickDetailResponse> details) {

    public static PickResponse from(PickMaster pickMaster) {
        BigDecimal profitRate = pickMaster.outputMoney() == null
                ? null
                : pickMaster.outputMoney()
                        .subtract(pickMaster.inputMoney())
                        .divide(pickMaster.inputMoney(), 4, RoundingMode.HALF_UP);

        return new PickResponse(
                pickMaster.id(),
                pickMaster.year(),
                pickMaster.round(),
                pickMaster.ymd(),
                pickMaster.userName(),
                pickMaster.inputMoney(),
                pickMaster.outputMoney(),
                profitRate,
                pickMaster.details().stream().map(PickDetailResponse::from).toList());
    }
}
