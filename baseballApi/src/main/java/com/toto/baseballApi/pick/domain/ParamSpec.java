package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * One tunable knob of an algorithm: an inclusive {@code [min, max]} range walked in {@code step}
 * increments, plus the value used when the knob is not being swept.
 *
 * <p>Grid values are computed as {@code min + i * step} and rounded to 6 decimals rather than
 * accumulated, so a 0.1 step never drifts into 1.7999999999.
 */
public record ParamSpec(String name, double min, double max, double step, double defaultValue) {

    private static final int SCALE = 6;

    public ParamSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ParamSpec name must not be blank");
        }
        if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step)
                || !Double.isFinite(defaultValue)) {
            throw new IllegalArgumentException("ParamSpec " + name + " values must be finite");
        }
        if (max < min) {
            throw new IllegalArgumentException("ParamSpec " + name + ": max must be >= min");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("ParamSpec " + name + ": step must be > 0");
        }
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(
                    "ParamSpec " + name + ": defaultValue must lie within [min, max]");
        }
    }

    /** A knob pinned to a single value — declared so it shows up in the space without widening it. */
    public static ParamSpec fixed(String name, double value) {
        return new ParamSpec(name, value, value, 1, value);
    }

    /** Range whose default is its midpoint-free lower bound; the common case when sweeping. */
    public static ParamSpec range(String name, double min, double max, double step) {
        return new ParamSpec(name, min, max, step, min);
    }

    public int cardinality() {
        return (int) Math.floor(round((max - min) / step)) + 1;
    }

    public List<Double> values() {
        List<Double> values = new ArrayList<>(cardinality());
        for (int i = 0; i < cardinality(); i++) {
            values.add(round(min + i * step));
        }
        return values;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
