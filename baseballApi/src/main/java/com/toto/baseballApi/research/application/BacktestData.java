package com.toto.baseballApi.research.application;

import java.util.List;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.pick.domain.MarketPairIndex;
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
 * @param days         flat-path units: one per {@code (year, round, ymd)}, in ymd order
 * @param stakingSlots staking-path units: one per {@code (ymd, PickUniverse#combinationBucket)}, in
 *                     chronological order. Kept as a separate list rather than replacing
 *                     {@link #days()} so that changing the staking unit cannot move a flat
 *                     algorithm's numbers — the two paths must stay comparable across the ledger
 * @param history      2-way games sorted by ymd ascending, so any prefix is exactly the games before
 *                     a given day — never re-sort or re-order it
 * @param formIndex    team form over that same history, shared by every day and every candidate
 * @param marketPairs  each picked 3-way game's 2-way ("야구 승패") counterpart, for algorithms that
 *                     bet that market. Built from the range's own 2-way listings rather than from
 *                     {@link #history()}, which deliberately stops before the last day
 */
public record BacktestData(
        List<PickDay> days, List<PickDay> stakingSlots,
        List<BaseballResult> history, TeamFormIndex formIndex, MarketPairIndex marketPairs) {

    /**
     * One selection unit — a day on the flat path, a slot on the staking path — pre-indexed for
     * settlement.
     *
     * @param historyPrefixLength how many entries of {@link BacktestData#history()} fall strictly
     *                            before this day — the boundary that keeps a backtest from seeing
     *                            its own future. Both slots of one ymd share it, and correctly so:
     *                            neither may see any game of its own day
     */
    public record PickDay(
            String ymd,
            List<BaseballResult> games,
            Map<Integer, BaseballResult> gamesById,
            int historyPrefixLength) {

        public List<BaseballResult> priorHistory(List<BaseballResult> history) {
            return history.subList(0, historyPrefixLength);
        }
    }

    /**
     * Distinct pick days in chronological order — what a train/validation split is cut on. Taken
     * from {@link #days()} only: both lists cover the same ymds, and cutting every path on the same
     * boundary is what keeps a staking candidate comparable to a flat one.
     */
    public List<String> distinctYmds() {
        return days.stream().map(PickDay::ymd).distinct().toList();
    }
}
