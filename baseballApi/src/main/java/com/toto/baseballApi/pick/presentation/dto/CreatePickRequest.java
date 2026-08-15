package com.toto.baseballApi.pick.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Exactly one of {@code algorithmName} or {@code manualPicks} must be supplied — enforced in
 * {@code PickService}, not here (no precedent for a custom cross-field {@code @Constraint} in
 * this codebase yet).
 */
public record CreatePickRequest(
        @NotNull Integer year,
        @NotNull Integer round,
        @NotBlank String userName,
        @NotNull @Positive BigDecimal inputMoney,
        String algorithmName,
        List<PickSelectionRequest> manualPicks) {
}
