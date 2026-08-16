package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * One slip after settlement: what was bet and what it paid. Produced by {@link PickBacktester} and
 * consumed either by the persisting simulation (which turns it into a {@code pick_mstr} row) or by
 * the in-memory research backtest (which only tallies it).
 */
public record SettledSlip(List<PickDetail> details, BigDecimal inputMoney, BigDecimal outputMoney) {

    /** A slip pays out or it pays nothing — there is no partial credit on a combination bet. */
    public boolean hit() {
        return outputMoney != null && outputMoney.compareTo(BigDecimal.ZERO) > 0;
    }
}
