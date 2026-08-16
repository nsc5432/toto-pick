package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SimulatePicksRequest(
        @NotBlank @Pattern(regexp = "\\d{6}") String bgngYmd,
        @NotBlank @Pattern(regexp = "\\d{6}") String endYmd,
        @Min(1) int num,
        @Positive double x,
        @Positive double y,
        @NotNull @Positive BigDecimal inputMoney,
        @Min(1) int combinedN) {
}
