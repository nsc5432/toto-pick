package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * One simulated slip with its legs joined back to the games — the read model behind the
 * slip-composition screen ("어떤 경기를 조합하여 픽했는지").
 *
 * @param legs one entry per {@code pick_dtl} row, enriched from {@code baseball_result}
 */
public record PickSlipView(
        Integer id,
        Integer year,
        Integer round,
        String ymd,
        String algorithmCode,
        BigDecimal inputMoney,
        BigDecimal outputMoney,
        boolean hit,
        List<Leg> legs) {

    /**
     * @param predicted  the backed side as persisted ("승" home / "1" draw / "패" away)
     * @param backedOdds the published price of the backed side; {@code null} when the game carries
     *                   no published odds
     * @param actualResult the realized {@code totalResult} of the game
     */
    public record Leg(
            Integer resultId,
            String ymd,
            String home,
            String away,
            String predicted,
            Double backedOdds,
            String actualResult,
            boolean legHit) {
    }
}
