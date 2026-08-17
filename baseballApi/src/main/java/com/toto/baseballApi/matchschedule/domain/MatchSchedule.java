package com.toto.baseballApi.matchschedule.domain;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * One upcoming fixture, as listed before it is played.
 *
 * <p>This is the input to a forward test — the only kind of backtest whose result cannot be
 * contaminated by the choice of parameters, because the games did not exist when the parameters were
 * frozen. Every number produced from {@code baseball_result} is measured on days that were available
 * while tuning; this is not.
 *
 * <p>{@code totalResult} and {@code totalDiv} are deliberately absent rather than nullable fields:
 * a fixture that has not been played has no outcome, and modelling it as "a result row with nulls"
 * is what would let an unplayed game leak into a settlement path by accident.
 */
public record MatchSchedule(
        Integer id,
        Integer year,
        Integer round,
        String tournament,
        String ymd,
        String tm,
        String home,
        String away,
        String gameType,
        String cond,
        Double pubHomeDiv,
        Double pubDrawDiv,
        Double pubAwayDiv) {

    /**
     * The same fixture shaped as a {@link BaseballResult} so the existing algorithms can select on it
     * unchanged.
     *
     * <p>Selection only ever reads identity and {@code publishedOdds()}; the outcome fields stay
     * {@code null} because there is no outcome yet. That is safe here and nowhere else: these objects
     * must never reach {@code PickSettlement}, which is why forward picks are settled later against
     * real result rows rather than against these.
     *
     * <p>The {@code id} carried over is this schedule row's id, not a {@code baseball_result} id —
     * the caller keeps the mapping and resolves the real fixture afterwards.
     */
    public BaseballResult asFixture() {
        return new BaseballResult(
                id, year, round, tournament, ymd, tm, home, away, gameType, cond,
                null, null, null, null,
                pubHomeDiv, pubDrawDiv, pubAwayDiv);
    }
}
