package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ParamSpaceTest {

    @Test
    void gridWalksEveryCombinationOfEveryKnob() {
        ParamSpace space = ParamSpace.of(
                new ParamSpec("a", 1, 3, 1, 1),
                new ParamSpec("b", 0, 1, 0.5, 0));

        assertThat(space.gridSize()).isEqualTo(9);
        assertThat(space.grid().stream().map(AlgorithmParams::signature))
                .containsExactlyInAnyOrder(
                        "a=1,b=0", "a=1,b=0.5", "a=1,b=1",
                        "a=2,b=0", "a=2,b=0.5", "a=2,b=1",
                        "a=3,b=0", "a=3,b=0.5", "a=3,b=1");
    }

    @Test
    void fractionalStepsLandOnCleanValuesRatherThanAccumulatingDrift() {
        // Naive accumulation gives 1.7999999999999998 at the 8th step, which would split one
        // logical candidate into two signatures and break both de-duplication and ledger lookup.
        List<Double> values = new ParamSpec("x", 1.0, 2.0, 0.1, 1.0).values();

        assertThat(values).hasSize(11);
        assertThat(values.get(8)).isEqualTo(1.8);
        assertThat(AlgorithmParams.of(java.util.Map.of("x", values.get(8))).signature()).isEqualTo("x=1.8");
    }

    @Test
    void exhaustiveGridIsUsedWhileItFitsTheBudget() {
        ParamSpace space = ParamSpace.of(new ParamSpec("a", 1, 4, 1, 1));

        assertThat(space.candidates(10, 42L)).hasSize(4);
    }

    @Test
    void oversizedGridFallsBackToADistinctSampleOfTheBudget() {
        ParamSpace space = ParamSpace.of(
                new ParamSpec("a", 0, 100, 1, 0),
                new ParamSpec("b", 0, 100, 1, 0));

        List<AlgorithmParams> candidates = space.candidates(50, 42L);

        assertThat(space.gridSize()).isEqualTo(101L * 101L);
        assertThat(candidates).hasSize(50).doesNotHaveDuplicates();
    }

    @Test
    void theSameSeedReproducesTheSameSample() {
        ParamSpace space = ParamSpace.of(new ParamSpec("a", 0, 100, 1, 0));

        assertThat(space.sample(20, 7L)).isEqualTo(space.sample(20, 7L));
    }

    @Test
    void samplingAnExhaustedSpaceTerminatesWithWhatExists() {
        ParamSpace space = ParamSpace.of(new ParamSpec("a", 1, 3, 1, 1));

        assertThat(space.sample(100, 1L)).hasSize(3);
    }

    @Test
    void anEmptySpaceStillYieldsExactlyOneRun() {
        assertThat(ParamSpace.empty().candidates(10, 1L))
                .containsExactly(AlgorithmParams.empty());
    }

    @Test
    void defaultsAreTakenFromEachSpec() {
        ParamSpace space = ParamSpace.of(
                new ParamSpec("a", 1, 5, 1, 3),
                new ParamSpec("b", 0, 1, 0.5, 0.5));

        assertThat(space.defaults().signature()).isEqualTo("a=3,b=0.5");
    }

    @Test
    void duplicateKnobNamesAreRejected() {
        assertThatThrownBy(() -> ParamSpace.of(
                new ParamSpec("a", 1, 2, 1, 1),
                new ParamSpec("a", 3, 4, 1, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void aDefaultOutsideTheRangeIsRejected() {
        assertThatThrownBy(() -> new ParamSpec("a", 1, 2, 1, 9))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
