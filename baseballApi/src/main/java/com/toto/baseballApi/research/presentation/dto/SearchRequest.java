package com.toto.baseballApi.research.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

import com.toto.baseballApi.research.application.BacktestSettings;
import com.toto.baseballApi.research.application.SearchCommand;

import jakarta.validation.constraints.NotBlank;

/**
 * A sweep request. Only the date range is required — every other field falls back to the configured
 * budget, so the routine call is two dates and a deviation has to be stated on purpose.
 *
 * <p>There is deliberately no way to override the goal here. The target lives in configuration
 * precisely so it cannot be lowered per-request to make a disappointing sweep report success.
 */
public record SearchRequest(
        @NotBlank String bgngYmd,
        @NotBlank String endYmd,
        Double trainRatio,
        Integer num,
        Double x,
        Double y,
        Integer combinedN,
        BigDecimal inputMoney,
        List<String> algorithmCodes,
        Integer maxCandidatesPerAlgorithm,
        Integer validationTopK,
        Long seed,
        String note) {

    public SearchCommand toCommand() {
        return new SearchCommand(
                bgngYmd,
                endYmd,
                trainRatio == null ? 0 : trainRatio,
                settings(num, x, y, combinedN, inputMoney),
                algorithmCodes,
                maxCandidatesPerAlgorithm == null ? 0 : maxCandidatesPerAlgorithm,
                validationTopK == null ? 0 : validationTopK,
                seed == null ? 0 : seed,
                null,
                note);
    }

    /**
     * Null when nothing was specified, so the service applies its own default; otherwise the
     * defaults with only the stated fields replaced.
     */
    static BacktestSettings settings(
            Integer num, Double x, Double y, Integer combinedN, BigDecimal inputMoney) {
        if (num == null && x == null && y == null && combinedN == null && inputMoney == null) {
            return null;
        }
        BacktestSettings base = BacktestSettings.DEFAULT;
        return new BacktestSettings(
                num == null ? base.num() : num,
                x == null ? base.x() : x,
                y == null ? base.y() : y,
                combinedN == null ? base.combinedN() : combinedN,
                inputMoney == null ? base.inputMoney() : inputMoney);
    }
}
