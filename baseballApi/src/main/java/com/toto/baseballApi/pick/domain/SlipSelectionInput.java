package com.toto.baseballApi.pick.domain;

import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * Everything an algorithm sees for one pick day.
 *
 * <p>{@code num}/{@code x}/{@code y}/{@code combinedN} are the shared knobs that predate
 * per-algorithm parameters; {@code params} carries an algorithm's own knobs during a search. When a
 * {@link TunableAlgorithm} declares one of the four standard names in its {@link ParamSpace}, the
 * backtester writes the swept value into both the matching component and {@code params}, so an
 * algorithm can read it from whichever it prefers and the two never disagree.
 *
 * @param dayGames     the 3-way ("야구 승1패") games of the single (year, round, ymd) being picked
 * @param historyGames 2-way ("야구 승패") games with {@code ymd} strictly before the target ymd,
 *                     already filtered to the eligible tournaments by the caller. Prefer
 *                     {@code formIndex} for team form; reach for this only when an algorithm needs
 *                     the raw games
 * @param formIndex    team form as of any date, built once per run and shared across days. It spans
 *                     the whole history rather than this day's slice, and is safe precisely because
 *                     every lookup names the date it must not see past — pass {@code ymd}
 * @param params       the algorithm's own knobs for this run; empty outside a parameter search
 */
public record SlipSelectionInput(
        String ymd,
        int num,
        double x,
        double y,
        int combinedN,
        List<BaseballResult> dayGames,
        List<BaseballResult> historyGames,
        TeamFormIndex formIndex,
        AlgorithmParams params) {

    public SlipSelectionInput {
        formIndex = formIndex == null ? TeamFormIndex.empty() : formIndex;
        params = params == null ? AlgorithmParams.empty() : params;
    }

    /**
     * Untuned input that derives its own form index from {@code historyGames}. Convenient for a
     * one-off call, but building the index per day is what a search must avoid — a sweep should
     * build it once and pass it to the full constructor.
     */
    public SlipSelectionInput(
            String ymd, int num, double x, double y, int combinedN,
            List<BaseballResult> dayGames, List<BaseballResult> historyGames) {
        this(ymd, num, x, y, combinedN, dayGames, historyGames,
                TeamFormIndex.build(historyGames), AlgorithmParams.empty());
    }

    /**
     * Same day, with the standard knobs overridden by {@code params} where present and the full
     * param bag attached. Values absent from {@code params} keep this input's own.
     */
    public SlipSelectionInput withParams(AlgorithmParams params) {
        AlgorithmParams resolved = params == null ? AlgorithmParams.empty() : params;
        return new SlipSelectionInput(
                ymd,
                resolved.getInt(AlgorithmParams.NUM, num),
                resolved.get(AlgorithmParams.X, x),
                resolved.get(AlgorithmParams.Y, y),
                resolved.getInt(AlgorithmParams.COMBINED_N, combinedN),
                dayGames,
                historyGames,
                formIndex,
                resolved);
    }
}
