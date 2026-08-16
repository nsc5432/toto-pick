package com.toto.baseballApi.research.presentation.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.toto.baseballApi.pick.domain.AlgorithmParams;
import com.toto.baseballApi.research.application.SearchCommand;

import jakarta.validation.constraints.NotBlank;

/**
 * Backtest one named configuration instead of sweeping — for re-checking a ledger entry, or for
 * scoring a hand-reasoned parameter set against the same split and the same goal a sweep uses.
 */
public record BacktestRequest(
        @NotBlank String algorithmCode,
        @NotBlank String bgngYmd,
        @NotBlank String endYmd,
        Map<String, Double> params,
        Double trainRatio,
        Integer num,
        Double x,
        Double y,
        Integer combinedN,
        BigDecimal inputMoney,
        String note) {

    public AlgorithmParams toParams() {
        return params == null ? AlgorithmParams.empty() : AlgorithmParams.of(params);
    }

    public SearchCommand toCommand() {
        return new SearchCommand(
                bgngYmd,
                endYmd,
                trainRatio == null ? 0 : trainRatio,
                SearchRequest.settings(num, x, y, combinedN, inputMoney),
                null,
                0,
                0,
                0,
                null,
                note);
    }
}
