package com.toto.baseballApi.research.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class BacktestMetricsTest {

    private DayOutcome day(String ymd, int slips, int hits, long input, long output) {
        return new DayOutcome(ymd, slips, hits, BigDecimal.valueOf(input), BigDecimal.valueOf(output));
    }

    @Test
    void ratesUseTheSameFormulasAsTheKpiDashboard() {
        BacktestMetrics metrics = BacktestMetrics.from(List.of(
                day("260601", 2, 1, 2000, 3000),
                day("260602", 2, 0, 2000, 0)));

        assertThat(metrics.slipCount()).isEqualTo(4);
        assertThat(metrics.hitCount()).isEqualTo(1);
        assertThat(metrics.profitRate()).isEqualByComparingTo("-0.2500");
        assertThat(metrics.hitRate()).isEqualByComparingTo("0.2500");
    }

    @Test
    void maxDrawdownMeasuresTheWorstFallFromAPeakNotTheFinalLoss() {
        // Cumulative profit: +5000 → -1000 → +2000. The peak is 5000 and the trough after it is
        // -1000, so the drawdown is 6000 even though the run ends up ahead.
        BacktestMetrics metrics = BacktestMetrics.from(List.of(
                day("260601", 1, 1, 1000, 6000),
                day("260602", 1, 0, 6000, 0),
                day("260603", 1, 1, 1000, 4000)));

        assertThat(metrics.maxDrawdown()).isEqualByComparingTo("6000");
    }

    @Test
    void drawdownIsZeroWhenTheRunNeverGivesBackAGain() {
        BacktestMetrics metrics = BacktestMetrics.from(List.of(
                day("260601", 1, 1, 1000, 3000),
                day("260602", 1, 1, 1000, 3000)));

        assertThat(metrics.maxDrawdown()).isEqualByComparingTo("0");
    }

    @Test
    void worstLosingStreakCountsConsecutiveUnderwaterDays() {
        BacktestMetrics metrics = BacktestMetrics.from(List.of(
                day("260601", 1, 0, 1000, 0),
                day("260602", 1, 0, 1000, 0),
                day("260603", 1, 1, 1000, 5000),
                day("260604", 1, 0, 1000, 0)));

        assertThat(metrics.worstLosingStreak()).isEqualTo(2);
    }

    @Test
    void standingAsideIsNeitherALosingDayNorABreakInALosingRun() {
        // The middle day produced no slip at all. Counting it as a win would reset the streak and
        // understate how long the strategy actually spent under water.
        BacktestMetrics metrics = BacktestMetrics.from(List.of(
                day("260601", 1, 0, 1000, 0),
                day("260602", 0, 0, 0, 0),
                day("260603", 1, 0, 1000, 0)));

        assertThat(metrics.worstLosingStreak()).isEqualTo(2);
        assertThat(metrics.dayCount()).isEqualTo(3);
        assertThat(metrics.bettingDayCount()).isEqualTo(2);
    }

    @Test
    void anEmptyWindowReportsNullRatesRatherThanAFabricatedZero() {
        BacktestMetrics metrics = BacktestMetrics.from(List.of());

        assertThat(metrics.profitRate()).isNull();
        assertThat(metrics.hitRate()).isNull();
        assertThat(metrics.profitRateOrZero()).isEqualByComparingTo("0");
    }
}
