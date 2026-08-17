package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One day's forward picks for one frozen configuration.
 *
 * <p>{@code params} is required rather than optional on purpose. A forward test is a claim about a
 * <em>specific</em> configuration, and letting it fall back to defaults would quietly test something
 * other than the candidate that motivated the run.
 */
public record GenerateForwardPicksCommand(
        String ymd,
        String algorithmCode,
        Map<String, Double> params,
        BigDecimal inputMoney,
        String userName) {
}
