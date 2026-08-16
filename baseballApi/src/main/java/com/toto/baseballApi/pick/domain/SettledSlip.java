package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * One slip after settlement: what was bet, what it paid, and what the market itself expected it to
 * pay. Produced by {@link PickBacktester} and consumed either by the persisting simulation (which
 * turns it into a {@code pick_mstr} row) or by the in-memory research backtest (which only tallies
 * it).
 *
 * @param benchmarkOutputMoney {@link PickSettlement#marketExpectation} for this slip — the payout an
 *                             uninformed bettor expects at these odds. The gap between it and
 *                             {@code outputMoney} is what the strategy actually contributed;
 *                             {@code null} when a leg had no published odds to quote it from
 * @param legs                 the same slip settled leg by leg — the low-variance view the goal is
 *                             measured on (see {@link LegTally})
 */
public record SettledSlip(
        List<PickDetail> details,
        BigDecimal inputMoney,
        BigDecimal outputMoney,
        BigDecimal benchmarkOutputMoney,
        LegTally legs) {

    /** A slip pays out or it pays nothing — there is no partial credit on a combination bet. */
    public boolean hit() {
        return outputMoney != null && outputMoney.compareTo(BigDecimal.ZERO) > 0;
    }
}
