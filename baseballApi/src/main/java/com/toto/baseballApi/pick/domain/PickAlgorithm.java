package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port for a pluggable pick-selection strategy (DIP boundary). Implementations live in
 * {@code infrastructure.algorithm} and are registered as named Spring beans so callers can
 * swap between them at runtime by name, without this port or its consumers changing.
 */
public interface PickAlgorithm {

    List<PickSelection> selectPicks(Integer year, Integer round, BigDecimal budget);
}
