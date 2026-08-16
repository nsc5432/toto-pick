package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The full search space of a {@link TunableAlgorithm} — every knob it exposes to a parameter sweep.
 *
 * <p>{@link #candidates(int, long)} is what a search actually calls: it enumerates the exhaustive
 * grid while that stays within budget and falls back to a seeded random sample once the grid would
 * blow past it. The seed makes a sweep reproducible, which matters because an experiment ledger is
 * only trustworthy if a recorded run can be re-run.
 */
public record ParamSpace(List<ParamSpec> specs) {

    public ParamSpace {
        specs = List.copyOf(specs);
        Set<String> names = new LinkedHashSet<>();
        for (ParamSpec spec : specs) {
            if (!names.add(spec.name())) {
                throw new IllegalArgumentException("Duplicate ParamSpec name: " + spec.name());
            }
        }
    }

    public static ParamSpace of(ParamSpec... specs) {
        return new ParamSpace(List.of(specs));
    }

    public static ParamSpace empty() {
        return new ParamSpace(List.of());
    }

    public boolean isEmpty() {
        return specs.isEmpty();
    }

    /** The params an algorithm runs with when nothing is being swept. */
    public AlgorithmParams defaults() {
        Map<String, Double> values = new LinkedHashMap<>();
        specs.forEach(spec -> values.put(spec.name(), spec.defaultValue()));
        return AlgorithmParams.of(values);
    }

    /** Size of the exhaustive grid, saturating at {@link Long#MAX_VALUE} rather than overflowing. */
    public long gridSize() {
        long size = 1;
        for (ParamSpec spec : specs) {
            long cardinality = spec.cardinality();
            if (size > Long.MAX_VALUE / cardinality) {
                return Long.MAX_VALUE;
            }
            size *= cardinality;
        }
        return size;
    }

    /** Exhaustive cartesian product. Guarded by the caller — check {@link #gridSize()} first. */
    public List<AlgorithmParams> grid() {
        List<Map<String, Double>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>());
        for (ParamSpec spec : specs) {
            List<Map<String, Double>> expanded = new ArrayList<>();
            for (Map<String, Double> row : rows) {
                for (Double value : spec.values()) {
                    Map<String, Double> next = new LinkedHashMap<>(row);
                    next.put(spec.name(), value);
                    expanded.add(next);
                }
            }
            rows = expanded;
        }
        return rows.stream().map(AlgorithmParams::of).toList();
    }

    /**
     * Up to {@code count} distinct random points, each knob drawn independently from its own grid
     * values (so samples land on the same lattice the exhaustive grid would).
     */
    public List<AlgorithmParams> sample(int count, long seed) {
        Random random = new Random(seed);
        List<List<Double>> valuesPerSpec = specs.stream().map(ParamSpec::values).toList();
        Set<AlgorithmParams> sampled = new LinkedHashSet<>();

        // Bounded so an exhausted space (fewer distinct points than requested) still terminates.
        int maxDraws = Math.max(count * 20, count + 100);
        for (int draw = 0; draw < maxDraws && sampled.size() < count; draw++) {
            Map<String, Double> values = new LinkedHashMap<>();
            for (int i = 0; i < specs.size(); i++) {
                List<Double> candidates = valuesPerSpec.get(i);
                values.put(specs.get(i).name(), candidates.get(random.nextInt(candidates.size())));
            }
            sampled.add(AlgorithmParams.of(values));
        }
        return List.copyOf(sampled);
    }

    /**
     * The candidate set a sweep evaluates: the exhaustive grid when it fits inside
     * {@code maxCandidates}, otherwise a seeded random sample of that size. An empty space yields a
     * single empty-params candidate so a non-tunable algorithm still runs exactly once.
     */
    public List<AlgorithmParams> candidates(int maxCandidates, long seed) {
        if (maxCandidates < 1) {
            throw new IllegalArgumentException("maxCandidates must be >= 1");
        }
        if (isEmpty()) {
            return List.of(AlgorithmParams.empty());
        }
        return gridSize() <= maxCandidates ? grid() : sample(maxCandidates, seed);
    }
}
