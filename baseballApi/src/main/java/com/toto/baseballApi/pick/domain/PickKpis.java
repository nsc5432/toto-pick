package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared KPI math for picks — the single home for the profit-rate and hit-rate formulas. */
public final class PickKpis {

    private PickKpis() {
    }

    /** {@code (output - input) / input}, scale 4 HALF_UP; {@code null} when either side is missing or input is 0. */
    public static BigDecimal profitRate(BigDecimal input, BigDecimal output) {
        if (input == null || output == null || input.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return output.subtract(input).divide(input, 4, RoundingMode.HALF_UP);
    }

    /** {@code hitCount / slipCount}, scale 4 HALF_UP; {@code null} when slipCount is 0. */
    public static BigDecimal hitRate(int hitCount, int slipCount) {
        if (slipCount == 0) {
            return null;
        }
        return BigDecimal.valueOf(hitCount).divide(BigDecimal.valueOf(slipCount), 4, RoundingMode.HALF_UP);
    }
}
