package com.toto.baseballApi.pick.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PickSelectionRequest(
        @NotNull Integer resultId,
        @NotBlank String totalResult) {
}
