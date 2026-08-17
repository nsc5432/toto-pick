package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.toto.baseballApi.pick.domain.ForwardPick;

/**
 * A recorded forward pick as the API exposes it — richer than {@link ForwardPickResponse}'s slip,
 * which only has to echo back what generation just wrote.
 *
 * <p>{@code createdAt} and {@code settledAt} are both here on purpose: the gap between them is the
 * proof the pick predates its games, and a reader has to be able to check that rather than take it on
 * trust. Same for the per-leg pair {@code predictedTotalResult} / {@code legResult} — the prediction
 * as recorded before the game, next to what actually happened.
 */
public record ForwardPickListResponse(
        Integer id,
        String algorithmCode,
        String algorithmName,
        String paramSignature,
        String userName,
        String ymd,
        String bucket,
        int slipNo,
        BigDecimal inputMoney,
        BigDecimal combinedOdds,
        String status,
        BigDecimal outputMoney,
        BigDecimal benchmarkOutputMoney,
        boolean hit,
        LocalDateTime createdAt,
        LocalDateTime settledAt,
        List<Leg> legs) {

    public record Leg(
            int legNo,
            String tournament,
            String ymd,
            String tm,
            String home,
            String away,
            String gameType,
            String predictedTotalResult,
            BigDecimal backedOdds,
            BigDecimal overround,
            Integer resultId,
            String legResult,
            Boolean legHit) {
    }

    public static ForwardPickListResponse from(ForwardPick pick, String algorithmName) {
        return new ForwardPickListResponse(
                pick.id(), pick.algorithmCode(), algorithmName, pick.paramSignature(),
                pick.userName(), pick.ymd(), pick.bucket(), pick.slipNo(),
                pick.inputMoney(), pick.combinedOdds(), pick.status().name(),
                pick.outputMoney(), pick.benchmarkOutputMoney(), pick.hit(),
                pick.createdAt(), pick.settledAt(),
                pick.legs().stream().map(leg -> toLeg(leg, pick.settled())).toList());
    }

    /** {@code legHit} stays null while unsettled — an unresolved leg is not a miss. */
    private static Leg toLeg(ForwardPick.ForwardPickLeg leg, boolean settled) {
        return new Leg(
                leg.legNo(), leg.fixture().tournament(), leg.fixture().ymd(), leg.fixture().tm(),
                leg.fixture().home(), leg.fixture().away(), leg.gameType(),
                leg.totalResult(), leg.backedOdds(), leg.overround(),
                leg.resultId(), leg.legResult(),
                settled && leg.legResult() != null ? leg.legHit() : null);
    }
}
