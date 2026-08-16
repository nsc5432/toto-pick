package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;

public record SimulatePicksCommand(
        String bgngYmd,
        String endYmd,
        int num,
        double x,
        double y,
        BigDecimal inputMoney,
        int combinedN) {
}
