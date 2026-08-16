package com.toto.baseballApi.research.application;

import java.util.List;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.pick.domain.TeamFormIndex;

/**
 * The whole game universe for a search range, loaded and indexed once, then reused by every
 * candidate.
 *
 * <p>A sweep evaluates hundreds of parameter points over the same days, so anything rebuilt per
 * candidate is paid for hundreds of times over. Three things are therefore built exactly once here:
 * the two queries, the {@link #formIndex()}, and {@link PickDay#historyPrefixLength()} — the last
 * turning "games before this day" into a {@code subList} view rather than a fresh filter pass per
 * day per candidate.
 *
 * @param history   2-way games sorted by ymd ascending, so any prefix is exactly the games before a
 *                  given day — never re-sort or re-order it
 * @param formIndex team form over that same history, shared by every day and every candidate
 */
public record BacktestData(
        List<PickDay> days, List<BaseballResult> history, TeamFormIndex formIndex) {

    /**
     * One pick day, pre-indexed for settlement.
     *
     * @param historyPrefixLength how many entries of {@link BacktestData#history()} fall strictly
     *                            before this day — the boundary that keeps a backtest from seeing
     *                            its own future
     */
    public record PickDay(
            Integer year,
            Integer round,
            String ymd,
            List<BaseballResult> games,
            Map<Integer, BaseballResult> gamesById,
            int historyPrefixLength) {

        public List<BaseballResult> priorHistory(List<BaseballResult> history) {
            return history.subList(0, historyPrefixLength);
        }
    }

    /** Distinct pick days in chronological order — what a train/validation split is cut on. */
    public List<String> distinctYmds() {
        return days.stream().map(PickDay::ymd).distinct().toList();
    }
}
