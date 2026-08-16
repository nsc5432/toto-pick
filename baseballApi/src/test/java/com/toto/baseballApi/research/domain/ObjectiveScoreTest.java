package com.toto.baseballApi.research.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ObjectiveScoreTest {

    private BacktestMetrics metrics(int slips, long input, long output) {
        return BacktestMetrics.from(List.of(new DayOutcome(
                "260601", slips, 0, BigDecimal.valueOf(input), BigDecimal.valueOf(output))));
    }

    @Test
    void anUnderSampledCandidateCannotOutrankAQualifiedOneNoMatterItsRate() {
        ObjectiveScore lucky = ObjectiveScore.of(metrics(5, 5_000, 25_000), 100);
        ObjectiveScore solid = ObjectiveScore.of(metrics(400, 400_000, 440_000), 100);

        assertThat(lucky.qualified()).isFalse();
        assertThat(lucky.profitRate()).isGreaterThan(solid.profitRate());
        assertThat(ObjectiveScore.BEST_FIRST.compare(solid, lucky)).isNegative();
    }

    @Test
    void amongQualifiedCandidatesTheHigherProfitRateWins() {
        ObjectiveScore better = ObjectiveScore.of(metrics(200, 200_000, 260_000), 100);
        ObjectiveScore worse = ObjectiveScore.of(metrics(200, 200_000, 220_000), 100);

        assertThat(ObjectiveScore.BEST_FIRST.compare(better, worse)).isNegative();
    }

    @Test
    void anEqualRateBreaksTowardTheLargerSample() {
        ObjectiveScore broad = ObjectiveScore.of(metrics(400, 400_000, 440_000), 100);
        ObjectiveScore narrow = ObjectiveScore.of(metrics(120, 120_000, 132_000), 100);

        assertThat(broad.profitRate()).isEqualByComparingTo(narrow.profitRate());
        assertThat(ObjectiveScore.BEST_FIRST.compare(broad, narrow)).isNegative();
    }
}
