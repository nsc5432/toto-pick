package com.toto.baseballApi.pick.application;

import java.util.List;

import com.toto.baseballApi.pick.domain.ForwardPick;

/**
 * @param scheduledGames fixtures the schedule held for this date
 * @param skippedSlips   slips the algorithm produced that were already on record — a re-run is a
 *                       no-op rather than a second bet, since the value of a pre-game record is that
 *                       it was written first
 */
public record ForwardPickResult(
        String ymd,
        String algorithmCode,
        String paramSignature,
        int scheduledGames,
        int skippedSlips,
        List<ForwardPick> picks) {
}
