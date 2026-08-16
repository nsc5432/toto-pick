package com.toto.baseballApi.research.application;

import java.math.BigDecimal;

import com.toto.baseballApi.pick.domain.AlgorithmParams;

/**
 * The betting conditions a search holds fixed, plus the fallback values for the standard knobs.
 *
 * <p>Every candidate in a sweep is run against the identical settings — same stake, same fallbacks —
 * so a difference in results is attributable to the algorithm and its parameters and nothing else.
 * A knob an algorithm does not declare in its {@link com.toto.baseballApi.pick.domain.ParamSpace}
 * takes its value from here rather than being left undefined.
 */
public record BacktestSettings(
        int num,
        double x,
        double y,
        int combinedN,
        BigDecimal inputMoney) {

    public static final BacktestSettings DEFAULT =
            new BacktestSettings(20, 1.80, 2.50, 3, BigDecimal.valueOf(1000));

    public BacktestSettings {
        if (num < 1) {
            throw new IllegalArgumentException("num must be >= 1");
        }
        if (combinedN < 1) {
            throw new IllegalArgumentException("combinedN must be >= 1");
        }
        if (inputMoney == null || inputMoney.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("inputMoney must be > 0");
        }
    }

    /** These settings as params, so a run without a sweep still records what it ran with. */
    public AlgorithmParams asParams() {
        return AlgorithmParams.empty()
                .with(AlgorithmParams.NUM, num)
                .with(AlgorithmParams.X, x)
                .with(AlgorithmParams.Y, y)
                .with(AlgorithmParams.COMBINED_N, combinedN);
    }
}
