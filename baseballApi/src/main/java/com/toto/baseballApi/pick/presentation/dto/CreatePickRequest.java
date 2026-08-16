package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Manual pick creation — algorithm picks are generated through {@code POST /api/picks/simulate}. */
public record CreatePickRequest(
        @NotNull Integer year,
        @NotNull Integer round,
        @NotBlank String userName,
        @NotNull @Positive BigDecimal inputMoney,
        @NotEmpty List<PickSelectionRequest> manualPicks) {
}
