package com.toto.baseballApi.pick.domain;

/**
 * A {@link PickAlgorithm} that declares which knobs a parameter search may sweep, and over what
 * ranges. Implementing this is what puts an algorithm into the optimization pipeline: the search
 * asks for {@link #paramSpace()}, walks it, and backtests each point.
 *
 * <p>An algorithm that implements only {@link PickAlgorithm} is still backtested and ranked — it is
 * simply evaluated once, at whatever the run's settings say. That is the right shape for a strategy
 * with genuinely nothing to tune; anything with a threshold in it should declare that threshold
 * here, including baselines, so it is compared at its own best rather than at an arbitrary point.
 *
 * <p>Declaring the four {@code AlgorithmParams.STANDARD} names ({@code num}, {@code x}, {@code y},
 * {@code combinedN}) sweeps the matching {@link SlipSelectionInput} components. Any other name is
 * delivered through {@link SlipSelectionInput#params()} for the algorithm to read itself — see
 * {@code MarketEdgeSlipAlgorithm} for that shape.
 */
public interface TunableAlgorithm extends PickAlgorithm {

    /** The knobs this algorithm exposes to a sweep. Must be stable across calls. */
    ParamSpace paramSpace();
}
