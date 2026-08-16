package com.toto.baseballApi.research.domain;

import java.util.List;

/**
 * A chronological train/validation split of the available pick days.
 *
 * <p>The split is always in time order — the earlier days train, the later days validate — never
 * random. A random split would let a candidate be tuned on days that come after the days it is
 * scored on, which is exactly the lookahead this pipeline exists to rule out. Parameters are chosen
 * on {@code train} and only ever reported from {@code validation}; a candidate that looks brilliant
 * on train and ordinary on validation was fitted to noise, and the gap between the two is the
 * cheapest overfitting detector available.
 */
public record BacktestSplit(BacktestWindow train, BacktestWindow validation) {

    /**
     * Splits the sorted distinct pick days so the first {@code trainRatio} of them train and the
     * rest validate. Both sides are guaranteed at least one day, so a range must hold at least two.
     */
    public static BacktestSplit byRatio(List<String> sortedDistinctYmds, double trainRatio) {
        if (sortedDistinctYmds.size() < 2) {
            throw new IllegalArgumentException(
                    "Need at least 2 distinct pick days to split; got " + sortedDistinctYmds.size());
        }
        if (trainRatio <= 0 || trainRatio >= 1) {
            throw new IllegalArgumentException("trainRatio must lie strictly between 0 and 1");
        }

        int trainDays = (int) Math.round(sortedDistinctYmds.size() * trainRatio);
        trainDays = Math.max(1, Math.min(sortedDistinctYmds.size() - 1, trainDays));

        return new BacktestSplit(
                new BacktestWindow(
                        BacktestWindow.TRAIN,
                        sortedDistinctYmds.get(0),
                        sortedDistinctYmds.get(trainDays - 1)),
                new BacktestWindow(
                        BacktestWindow.VALIDATION,
                        sortedDistinctYmds.get(trainDays),
                        sortedDistinctYmds.get(sortedDistinctYmds.size() - 1)));
    }
}
