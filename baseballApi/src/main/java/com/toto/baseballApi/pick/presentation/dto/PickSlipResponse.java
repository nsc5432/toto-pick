package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

import com.toto.baseballApi.pick.application.PickSlipView;

public record PickSlipResponse(
        Integer id,
        Integer year,
        Integer round,
        String ymd,
        String algorithmCode,
        BigDecimal inputMoney,
        BigDecimal outputMoney,
        boolean hit,
        List<PickSlipLegResponse> legs) {

    public static PickSlipResponse from(PickSlipView view) {
        return new PickSlipResponse(
                view.id(),
                view.year(),
                view.round(),
                view.ymd(),
                view.algorithmCode(),
                view.inputMoney(),
                view.outputMoney(),
                view.hit(),
                view.legs().stream().map(PickSlipLegResponse::from).toList());
    }

    public record PickSlipLegResponse(
            Integer resultId,
            String ymd,
            String home,
            String away,
            String predicted,
            Double backedOdds,
            String actualResult,
            boolean legHit) {

        static PickSlipLegResponse from(PickSlipView.Leg leg) {
            return new PickSlipLegResponse(
                    leg.resultId(), leg.ymd(), leg.home(), leg.away(),
                    leg.predicted(), leg.backedOdds(), leg.actualResult(), leg.legHit());
        }
    }
}
