package com.toto.baseballApi.research.domain;

/**
 * A labelled, inclusive ymd range a backtest is scored over.
 *
 * <p>The label matters as much as the dates: a number quoted without saying whether it came from
 * {@code TRAIN} or {@code VALIDATION} is the single easiest way to mistake curve-fitting for skill.
 */
public record BacktestWindow(String label, String bgngYmd, String endYmd) {

    public static final String TRAIN = "TRAIN";
    public static final String VALIDATION = "VALIDATION";

    public BacktestWindow {
        if (bgngYmd == null || endYmd == null) {
            throw new IllegalArgumentException("BacktestWindow bounds must not be null");
        }
        if (bgngYmd.compareTo(endYmd) > 0) {
            throw new IllegalArgumentException("bgngYmd must not be after endYmd: " + bgngYmd + ".." + endYmd);
        }
    }

    public boolean contains(String ymd) {
        return ymd.compareTo(bgngYmd) >= 0 && ymd.compareTo(endYmd) <= 0;
    }
}
