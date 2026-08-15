package com.toto.baseballApi.pick.infrastructure.algorithm;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickSelection;

import lombok.RequiredArgsConstructor;

/**
 * Naive sample {@link PickAlgorithm} — exists to prove the DIP wiring (algorithms are
 * registered as named beans and swapped by name in {@code PickService}), not as a real
 * predictive strategy.
 *
 * <p>Picks the {@value #LEG_COUNT} games with the highest {@code totalDiv} for the given
 * year/round and always predicts the same fixed category ("오버"). It deliberately never
 * reads a candidate's actual {@code totalResult} — doing so would let it "cheat" and always
 * hit on settlement, which would defeat the purpose of exercising the hit/miss logic.
 */
@Component("highestOddsPickAlgorithm")
@RequiredArgsConstructor
public class HighestOddsPickAlgorithm implements PickAlgorithm {

    private static final int LEG_COUNT = 3;
    private static final String NAIVE_PREDICTION = "오버";

    private final BaseballResultRepository baseballResultRepository;

    @Override
    public List<PickSelection> selectPicks(Integer year, Integer round, BigDecimal budget) {
        return baseballResultRepository.findByYearAndRound(year, round).stream()
                .sorted(Comparator.comparingDouble(BaseballResult::totalDiv).reversed())
                .limit(LEG_COUNT)
                .map(result -> new PickSelection(result.id(), NAIVE_PREDICTION))
                .toList();
    }
}
