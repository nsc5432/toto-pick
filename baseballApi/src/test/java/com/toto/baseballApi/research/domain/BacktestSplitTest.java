package com.toto.baseballApi.research.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class BacktestSplitTest {

    private List<String> days(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(day -> String.format("2606%02d", day))
                .toList();
    }

    @Test
    void trainTakesTheEarlierDaysAndValidationTheLater() {
        BacktestSplit split = BacktestSplit.byRatio(days(10), 0.7);

        assertThat(split.train().bgngYmd()).isEqualTo("260601");
        assertThat(split.train().endYmd()).isEqualTo("260607");
        assertThat(split.validation().bgngYmd()).isEqualTo("260608");
        assertThat(split.validation().endYmd()).isEqualTo("260610");
    }

    @Test
    void theTwoWindowsNeverOverlap() {
        BacktestSplit split = BacktestSplit.byRatio(days(37), 0.65);

        assertThat(split.train().endYmd()).isLessThan(split.validation().bgngYmd());
        assertThat(days(37).stream().filter(split.train()::contains))
                .doesNotContainAnyElementsOf(days(37).stream().filter(split.validation()::contains).toList());
    }

    @Test
    void bothSidesKeepAtLeastOneDayEvenAtAnExtremeRatio() {
        BacktestSplit wide = BacktestSplit.byRatio(days(4), 0.99);
        assertThat(wide.validation().bgngYmd()).isEqualTo("260604");

        BacktestSplit narrow = BacktestSplit.byRatio(days(4), 0.01);
        assertThat(narrow.train().endYmd()).isEqualTo("260601");
    }

    @Test
    void aSingleDayCannotBeSplit() {
        assertThatThrownBy(() -> BacktestSplit.byRatio(days(1), 0.7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    void aRatioOutsideZeroToOneIsRejected() {
        assertThatThrownBy(() -> BacktestSplit.byRatio(days(10), 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BacktestSplit.byRatio(days(10), 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
