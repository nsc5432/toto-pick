package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;

/** {@code algorithmCodes} null/empty means "run every registered algorithm". */
public record SimulatePicksCommand(
        String bgngYmd,
        String endYmd,
        int num,
        double x,
        double y,
        BigDecimal inputMoney,
        int combinedN,
        List<String> algorithmCodes) {
}
