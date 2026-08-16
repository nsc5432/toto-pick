package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable name→value bag of one algorithm's knobs — the unit a parameter search sweeps and the
 * unit an experiment is keyed by. Values are doubles even for whole-number knobs so a single
 * {@link ParamSpec} grid can express both; read them back with {@link #getInt(String, int)} where
 * an int is wanted.
 *
 * <p>The four {@code STANDARD_*} names are the knobs that predate per-algorithm parameters and are
 * still carried as dedicated {@link SlipSelectionInput} components; an algorithm may declare them in
 * its {@link ParamSpace} like any other name, and the backtester feeds the swept value into the
 * matching component. Any other name stays in {@link SlipSelectionInput#params()} for the algorithm
 * to read itself.
 */
public final class AlgorithmParams {

    /** Rolling window size (games per team) behind a win-rate ranking. */
    public static final String NUM = "num";
    /** Lower bound on a strong team's win odds ("back the strong team when it still pays x"). */
    public static final String X = "x";
    /** Upper bound on a weak team's win odds ("fade the weak team when it pays at most y"). */
    public static final String Y = "y";
    /** Number of legs combined into one slip. */
    public static final String COMBINED_N = "combinedN";

    private static final AlgorithmParams EMPTY = new AlgorithmParams(Map.of());

    private final Map<String, Double> values;

    private AlgorithmParams(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public static AlgorithmParams empty() {
        return EMPTY;
    }

    public static AlgorithmParams of(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return EMPTY;
        }
        values.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Parameter name must not be blank");
            }
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Parameter " + name + " must be a finite number");
            }
        });
        return new AlgorithmParams(values);
    }

    /** Sorted by name, so iteration order and {@link #signature()} are stable. */
    public Map<String, Double> values() {
        return values;
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public double get(String name, double fallback) {
        Double value = values.get(name);
        return value == null ? fallback : value;
    }

    public int getInt(String name, int fallback) {
        Double value = values.get(name);
        return value == null ? fallback : (int) Math.round(value);
    }

    public AlgorithmParams with(String name, double value) {
        Map<String, Double> merged = new LinkedHashMap<>(values);
        merged.put(name, value);
        return of(merged);
    }

    /**
     * Canonical {@code "combinedN=3,num=20,x=1.8,y=2.5"} — the stable identity used to de-duplicate
     * candidates during a sweep and to key an experiment in the ledger. Empty params render as
     * {@code "default"}.
     */
    public String signature() {
        if (values.isEmpty()) {
            return "default";
        }
        StringBuilder sb = new StringBuilder();
        values.forEach((name, value) -> {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(name).append('=').append(format(value));
        });
        return sb.toString();
    }

    /** Trims to 6 decimals and strips trailing zeros so 1.7999999999 and 1.8 share one signature. */
    private static String format(double value) {
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof AlgorithmParams params && signature().equals(params.signature());
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature());
    }

    @Override
    public String toString() {
        return signature();
    }
}
