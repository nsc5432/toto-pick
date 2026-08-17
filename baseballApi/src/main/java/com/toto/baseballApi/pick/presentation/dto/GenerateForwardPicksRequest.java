package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.toto.baseballApi.pick.application.GenerateForwardPicksCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param params the frozen configuration under test. Required — a forward test with unstated
 *               parameters is not testing the candidate that motivated it
 */
public record GenerateForwardPicksRequest(
        @NotBlank String ymd,
        @NotBlank String algorithmCode,
        @NotNull Map<String, Double> params,
        BigDecimal inputMoney,
        String userName) {

    private static final BigDecimal DEFAULT_INPUT_MONEY = BigDecimal.valueOf(1000);
    private static final String DEFAULT_USER_NAME = "NSC";

    public GenerateForwardPicksCommand toCommand() {
        return new GenerateForwardPicksCommand(
                ymd,
                algorithmCode,
                params,
                inputMoney == null ? DEFAULT_INPUT_MONEY : inputMoney,
                userName == null || userName.isBlank() ? DEFAULT_USER_NAME : userName);
    }
}
