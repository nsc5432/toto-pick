package com.toto.baseballApi.pick.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PickKpisTest {

    @Test
    void profitRateIsGainOverInputAtScale4() {
        assertEquals(new BigDecimal("0.2345"),
                PickKpis.profitRate(new BigDecimal("10000"), new BigDecimal("12345")));
    }

    @Test
    void profitRateIsNegativeOnLoss() {
        assertEquals(new BigDecimal("-1.0000"),
                PickKpis.profitRate(new BigDecimal("10000"), BigDecimal.ZERO));
    }

    @Test
    void profitRateIsNullWhenInputMissingOrZeroOrOutputMissing() {
        assertNull(PickKpis.profitRate(null, BigDecimal.ONE));
        assertNull(PickKpis.profitRate(BigDecimal.ZERO, BigDecimal.ONE));
        assertNull(PickKpis.profitRate(BigDecimal.ONE, null));
    }

    @Test
    void hitRateIsHitsOverSlipsAtScale4() {
        assertEquals(new BigDecimal("0.3333"), PickKpis.hitRate(1, 3));
        assertEquals(new BigDecimal("0.6667"), PickKpis.hitRate(2, 3));
    }

    @Test
    void hitRateIsNullWhenNoSlips() {
        assertNull(PickKpis.hitRate(0, 0));
    }
}
