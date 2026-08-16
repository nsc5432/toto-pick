package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;

import com.toto.baseballApi.pick.domain.PickSelection;

/** Manual pick creation — algorithm-generated picks are produced by the simulation instead. */
public record CreatePickCommand(
        Integer year,
        Integer round,
        String userName,
        BigDecimal inputMoney,
        List<PickSelection> manualPicks) {
}
