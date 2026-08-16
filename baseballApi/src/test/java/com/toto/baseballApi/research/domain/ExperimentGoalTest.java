package com.toto.baseballApi.research.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExperimentGoalTest {

    private static final ExperimentGoal GOAL =
            new ExperimentGoal(new BigDecimal("0.10"), 100, 20, null, null);

    private BacktestMetrics metrics(int slips, int bettingDays, long input, long output) {
        return BacktestMetrics.from(java.util.stream.IntStream.range(0, bettingDays)
                .mapToObj(day -> new DayOutcome(
                        String.format("2606%02d", day + 1),
                        slips / bettingDays, 0,
                        BigDecimal.valueOf(input / bettingDays),
                        BigDecimal.valueOf(output / bettingDays)))
                .toList());
    }

    @Test
    void clearingEveryRequirementPasses() {
        GoalVerdict verdict = GOAL.evaluate(metrics(200, 40, 200_000, 240_000));

        assertThat(verdict.achieved()).isTrue();
        assertThat(verdict.failedChecks()).isEmpty();
    }

    @Test
    void aStrongProfitRateOnTooFewSlipsStillFails() {
        // +200% on 20 slips is the exact shape of a lucky run, and it must not be reported as a hit.
        GoalVerdict verdict = GOAL.evaluate(metrics(20, 20, 20_000, 60_000));

        assertThat(verdict.achieved()).isFalse();
        assertThat(verdict.failedChecks()).extracting(GoalCheck::name).containsExactly("최소 조합 수");
    }

    @Test
    void missingTheProfitRateFailsAndSaysBothTheBarAndTheActual() {
        GoalVerdict verdict = GOAL.evaluate(metrics(200, 40, 200_000, 204_000));

        assertThat(verdict.achieved()).isFalse();
        assertThat(verdict.failureSummary()).contains("수익률", "0.10", "0.0200");
    }

    @Test
    void anEmptyWindowFailsRatherThanPassingVacuously() {
        GoalVerdict verdict = GOAL.evaluate(BacktestMetrics.from(List.of()));

        assertThat(verdict.achieved()).isFalse();
        assertThat(verdict.checks()).anyMatch(check -> "표본 없음".equals(check.actual()));
    }

    @Test
    void optionalRequirementsAreOnlyCheckedWhenDeclared() {
        BacktestMetrics passing = metrics(200, 40, 200_000, 240_000);

        assertThat(GOAL.evaluate(passing).checks()).hasSize(3);
        assertThat(new ExperimentGoal(new BigDecimal("0.10"), 100, 20, new BigDecimal("0.90"), null)
                .evaluate(passing).achieved()).isFalse();
    }

    @Test
    void aGoalWithNoSampleGuardIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new ExperimentGoal(new BigDecimal("0.10"), 0, 20, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
