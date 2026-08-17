package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.toto.baseballApi.pick.application.ForwardPickResult;
import com.toto.baseballApi.pick.domain.ForwardPick;

public record ForwardPickResponse(
        String ymd,
        String algorithmCode,
        String paramSignature,
        int scheduledGames,
        int skippedSlips,
        List<Slip> picks) {

    public record Slip(
            Integer id,
            String bucket,
            int slipNo,
            BigDecimal inputMoney,
            BigDecimal combinedOdds,
            String status,
            LocalDateTime createdAt,
            List<Leg> legs) {
    }

    public record Leg(
            int legNo,
            String tournament,
            String ymd,
            String tm,
            String home,
            String away,
            String gameType,
            String predictedTotalResult,
            BigDecimal backedOdds) {
    }

    public static ForwardPickResponse from(ForwardPickResult result) {
        return new ForwardPickResponse(
                result.ymd(), result.algorithmCode(), result.paramSignature(),
                result.scheduledGames(), result.skippedSlips(),
                result.picks().stream().map(ForwardPickResponse::toSlip).toList());
    }

    private static Slip toSlip(ForwardPick pick) {
        return new Slip(
                pick.id(), pick.bucket(), pick.slipNo(), pick.inputMoney(), pick.combinedOdds(),
                pick.status().name(), pick.createdAt(),
                pick.legs().stream().map(ForwardPickResponse::toLeg).toList());
    }

    private static Leg toLeg(ForwardPick.ForwardPickLeg leg) {
        return new Leg(
                leg.legNo(), leg.fixture().tournament(), leg.fixture().ymd(), leg.fixture().tm(),
                leg.fixture().home(), leg.fixture().away(), leg.gameType(),
                leg.totalResult(), leg.backedOdds());
    }
}
