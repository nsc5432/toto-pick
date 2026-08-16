package com.toto.baseballApi.research.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.pick.domain.LegTally;

class ObjectiveScoreTest {

    /** No-skill value of one leg at the measured 15.29% overround: 1/1.1529 of the stake. */
    private static final double NO_SKILL_LEG = 0.8674;

    /** Metrics whose legs beat the market benchmark by {@code legExcess} per staked unit. */
    private BacktestMetrics metrics(int slips, double legExcess) {
        int legs = slips * 2;
        BigDecimal benchmark = BigDecimal.valueOf(legs * NO_SKILL_LEG);
        return BacktestMetrics.from(List.of(new DayOutcome(
                "260601", slips, 0,
                BigDecimal.valueOf(slips * 1_000L), BigDecimal.valueOf(slips * 800L), null,
                new LegTally(legs, 0, benchmark.add(BigDecimal.valueOf(legs * legExcess)), benchmark))));
    }

    @Test
    void anUnderSampledCandidateCannotOutrankAQualifiedOneNoMatterItsEdge() {
        ObjectiveScore lucky = ObjectiveScore.of(metrics(5, 0.80), 100);
        ObjectiveScore solid = ObjectiveScore.of(metrics(400, 0.04), 100);

        assertThat(lucky.qualified()).isFalse();
        assertThat(lucky.excessReturn()).isGreaterThan(solid.excessReturn());
        assertThat(ObjectiveScore.BEST_FIRST.compare(solid, lucky)).isNegative();
    }

    @Test
    void amongQualifiedCandidatesTheLargerEdgeWins() {
        ObjectiveScore better = ObjectiveScore.of(metrics(200, 0.10), 100);
        ObjectiveScore worse = ObjectiveScore.of(metrics(200, 0.02), 100);

        assertThat(ObjectiveScore.BEST_FIRST.compare(better, worse)).isNegative();
    }

    @Test
    void anEqualEdgeBreaksTowardTheLargerSample() {
        ObjectiveScore broad = ObjectiveScore.of(metrics(400, 0.05), 100);
        ObjectiveScore narrow = ObjectiveScore.of(metrics(120, 0.05), 100);

        assertThat(broad.excessReturn()).isEqualByComparingTo(narrow.excessReturn());
        assertThat(ObjectiveScore.BEST_FIRST.compare(broad, narrow)).isNegative();
    }

    @Test
    void aLosingRunOutranksAWorseOneWhenItLosesLessThanTheMarketExpects() {
        // Both lose money — every slip here pays back 800 of a 1,000 stake. Ranking on profit rate
        // could not tell them apart at all; ranking on the edge their legs showed can.
        ObjectiveScore withEdge = ObjectiveScore.of(metrics(200, 0.06), 100);
        ObjectiveScore withoutEdge = ObjectiveScore.of(metrics(200, -0.06), 100);

        assertThat(withEdge.excessReturn()).isPositive();
        assertThat(withoutEdge.excessReturn()).isNegative();
        assertThat(ObjectiveScore.BEST_FIRST.compare(withEdge, withoutEdge)).isNegative();
    }
}
