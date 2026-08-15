package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;

import com.toto.baseballApi.pick.domain.PickSelection;

/**
 * Either {@code algorithmName} (delegate selection to a registered {@code PickAlgorithm})
 * or {@code manualPicks} (caller-supplied selections) must be provided, not both.
 */
public record CreatePickCommand(
        Integer year,
        Integer round,
        String userName,
        BigDecimal inputMoney,
        String algorithmName,
        List<PickSelection> manualPicks) {
}
