package com.toto.baseballApi.pick.domain;

import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * Input for {@link WinRateOddsSlipSelector}.
 *
 * @param dayGames     the 3-way ("야구 승1패") games of the single (year, round, ymd) being picked
 * @param historyGames 2-way ("야구 승패") games with {@code ymd} strictly before the target ymd,
 *                     already filtered to the eligible tournaments by the caller
 */
public record SlipSelectionInput(
        String ymd,
        int num,
        double x,
        double y,
        int combinedN,
        List<BaseballResult> dayGames,
        List<BaseballResult> historyGames) {
}
