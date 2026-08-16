package com.toto.baseballApi.research.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.pick.domain.LegTally;

class ExperimentGoalTest {

    private static final ExperimentGoal GOAL =
            new ExperimentGoal(new BigDecimal("0.10"), 100, 20, null, null, null, null, null, null, null);

    /** No-skill value of one leg at the measured 15.29% overround: 1/1.1529 of the stake. */
    private static final double NO_SKILL_LEG = 0.8674;

    private BacktestMetrics metrics(int slips, int bettingDays, long input, long output) {
        return metrics(slips, bettingDays, input, output, 0.0);
    }

    /** As above, with the legs beating the market benchmark by {@code legExcess} per staked unit. */
    private BacktestMetrics metrics(
            int slips, int bettingDays, long input, long output, double legExcess) {
        int legsPerDay = slips / bettingDays * 2;
        BigDecimal benchmark = BigDecimal.valueOf(legsPerDay * NO_SKILL_LEG);
        return BacktestMetrics.from(java.util.stream.IntStream.range(0, bettingDays)
                .mapToObj(day -> new DayOutcome(
                        String.format("2606%02d", day + 1),
                        slips / bettingDays, 0,
                        BigDecimal.valueOf(input / bettingDays),
                        BigDecimal.valueOf(output / bettingDays),
                        null,
                        new LegTally(legsPerDay, 0,
                                benchmark.add(BigDecimal.valueOf(legsPerDay * legExcess)),
                                benchmark)))
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
        assertThat(new ExperimentGoal(
                new BigDecimal("0.10"), 100, 20, new BigDecimal("0.90"), null, null, null, null, null, null)
                .evaluate(passing).achieved()).isFalse();
    }

    @Test
    void aGoalWithNoSampleGuardIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new ExperimentGoal(new BigDecimal("0.10"), 0, 20, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWindowThatDisagreesWithTrainingByMoreThanTheGapFails() {
        // The §10 false positive in miniature: no edge where the parameters were chosen, a large
        // one on the held-out window. The validation number alone would pass.
        ExperimentGoal guarded = new ExperimentGoal(
                new BigDecimal("0.10"), 100, 20, null, null, new BigDecimal("0.15"), null, null, null, null);
        BacktestMetrics train = metrics(400, 40, 400_000, 300_000, -0.02);
        BacktestMetrics validation = metrics(200, 40, 200_000, 260_000, 0.30);

        assertThat(guarded.evaluate(validation).achieved()).isTrue();
        GoalVerdict verdict = guarded.evaluate(train, validation);

        assertThat(verdict.achieved()).isFalse();
        assertThat(verdict.failedChecks()).extracting(GoalCheck::name).containsExactly("학습·검증 격차");
    }

    @Test
    void twoWindowsThatAgreeClearTheStabilityGuard() {
        ExperimentGoal guarded = new ExperimentGoal(
                new BigDecimal("0.10"), 100, 20, null, null, new BigDecimal("0.15"), null, null, null, null);
        BacktestMetrics train = metrics(400, 40, 400_000, 440_000, 0.06);
        BacktestMetrics validation = metrics(200, 40, 200_000, 240_000, 0.09);

        assertThat(guarded.evaluate(train, validation).achieved()).isTrue();
    }

    @Test
    void profitConcentratedInOneSegmentFailsTheSegmentGuard() {
        // 40 betting days: the last 10 carry every win, the other 30 lose steadily.
        List<DayOutcome> days = new java.util.ArrayList<>();
        for (int day = 0; day < 40; day++) {
            long output = day < 30 ? 3_000 : 30_000;
            days.add(new DayOutcome(String.format("2606%02d", day + 1), 5, 0,
                    BigDecimal.valueOf(5_000), BigDecimal.valueOf(output)));
        }
        BacktestMetrics tailDriven = BacktestMetrics.from(days);
        ExperimentGoal guarded = new ExperimentGoal(
                new BigDecimal("0.10"), 100, 20, null, null, null, 3, null, null, null);

        assertThat(tailDriven.profitRate()).isGreaterThan(new BigDecimal("0.10"));
        assertThat(tailDriven.profitableSegmentCount()).isEqualTo(1);
        assertThat(guarded.evaluate(tailDriven).achieved()).isFalse();
    }

    @Test
    void aGoalStatingNeitherAProfitTargetNorAnEdgeTargetIsRejected() {
        assertThatThrownBy(() -> new ExperimentGoal(null, 100, 20, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theEdgeTargetPassesAStrategyThatLosesMoneyButBeatsTheMarket() {
        // Loses 5% of stake, yet its legs returned 8%p more than those prices were worth. An
        // absolute profit target calls this a failure; the edge target calls it what it is — a
        // signal, and the only thing that could ever grow into a profitable one.
        ExperimentGoal edgeGoal = new ExperimentGoal(
                null, 100, 20, null, null, null, null, new BigDecimal("0.05"), null, null);
        BacktestMetrics losingWithEdge = metrics(200, 40, 200_000, 190_000, 0.08);

        assertThat(losingWithEdge.profitRate()).isNegative();
        assertThat(edgeGoal.evaluate(losingWithEdge).achieved()).isTrue();
    }

    @Test
    void theEdgeTargetRejectsAStrategyThatOnlyMatchesTheMarket() {
        ExperimentGoal edgeGoal = new ExperimentGoal(
                null, 100, 20, null, null, null, null, new BigDecimal("0.05"), null, null);
        BacktestMetrics noEdge = metrics(200, 40, 200_000, 240_000, 0.0);

        // Profitable on the window, but its picks were worth exactly what any picks would have been.
        assertThat(noEdge.profitRate()).isPositive();
        assertThat(noEdge.legExcessReturn()).isEqualByComparingTo("0");
        assertThat(edgeGoal.evaluate(noEdge).achieved()).isFalse();
    }

    @Test
    void theSameEdgeClearsSignificanceOnALargeSampleAndFailsItOnASmallOne() {
        // Sample gates relaxed so significance is the only thing separating the two.
        ExperimentGoal significant = new ExperimentGoal(
                null, 10, 5, null, null, null, null,
                new BigDecimal("0.02"), new BigDecimal("2.0"), null);

        // The same +8%p edge at the same payout spread — only the leg count differs.
        BacktestMetrics wide = legsWithEdge(2_000, 0.08);
        BacktestMetrics narrow = legsWithEdge(150, 0.08);

        assertThat(wide.legExcessReturn())
                .isCloseTo(narrow.legExcessReturn(), within(new BigDecimal("0.01")));
        assertThat(significant.evaluate(wide).achieved()).isTrue();
        assertThat(significant.evaluate(narrow).achieved()).isFalse();
        assertThat(significant.evaluate(narrow).failedChecks())
                .extracting(GoalCheck::name).containsExactly("초과수익 유의성(t)");
    }

    /** One betting day per 5 legs, every leg paying 2.5 on a win, sized to give exactly {@code edge}. */
    private BacktestMetrics legsWithEdge(int legCount, double edge) {
        double odds = 2.5;
        double perLegPayout = NO_SKILL_LEG + edge;
        int hits = (int) Math.round(legCount * perLegPayout / odds);
        List<DayOutcome> days = new java.util.ArrayList<>();
        int legsPerDay = 5;
        int dayCount = legCount / legsPerDay;
        for (int day = 0; day < dayCount; day++) {
            int dayHits = hits / dayCount + (day < hits % dayCount ? 1 : 0);
            days.add(new DayOutcome(
                    String.format("2606%02d", day + 1), legsPerDay / 2, 0,
                    BigDecimal.valueOf(2_000), BigDecimal.valueOf(1_500), null,
                    new LegTally(legsPerDay, dayHits,
                            BigDecimal.valueOf(dayHits * odds),
                            BigDecimal.valueOf(legsPerDay * NO_SKILL_LEG),
                            BigDecimal.valueOf(dayHits * odds * odds))));
        }
        return BacktestMetrics.from(days);
    }
}
