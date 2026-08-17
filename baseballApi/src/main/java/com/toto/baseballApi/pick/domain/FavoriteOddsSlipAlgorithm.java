package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;

/**
 * Favorite-backing baseline strategy over 3-way ("야구 승1패") games: for every day game with
 * published odds, back whichever side the market favors (the lower odds). Candidates are
 * sorted by the predicted side's odds ascending and chunked into slips of exactly
 * {@code combinedN}; the trailing incomplete chunk is discarded. Ignores {@code num}/{@code x}/
 * {@code y} — it needs no history. Exists as the market-consensus baseline the other algorithms
 * have to beat.
 *
 * <p>{@code combinedN} is swept here too, so the baseline is measured at its own best leg count
 * rather than at whatever the other algorithms happened to be run with. A strategy that only beats
 * a handicapped baseline has not been shown to beat the market.
 */
public class FavoriteOddsSlipAlgorithm implements TunableAlgorithm {

    public static final String CODE = "FAVORITE";

    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "배당 우세팀";
    }

    @Override
    public ParamSpace paramSpace() {
        // 1~3 legs (설계 결정 D1, 2026-08-17 revision) — a baseline swept over leg counts the real
        // candidates are barred from would not be the same comparison. N=1 was excluded until a
        // single-game 프로토 승부식 purchase was confirmed possible; it is now the theoretical best
        // case, since parlay math only ever costs excess return as legs are added.
        return ParamSpace.of(new ParamSpec(AlgorithmParams.COMBINED_N, 1, 3, 1, 3));
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        record Candidate(Integer resultId, String predicted, double odds) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (BaseballResult game : input.dayGames()) {
            ThreeWayOdds odds = game.publishedOdds();
            if (odds == null || odds.home() == odds.away()) {
                continue;
            }
            boolean homeFavored = odds.home() < odds.away();
            candidates.add(new Candidate(
                    game.id(),
                    homeFavored ? HOME_WIN : AWAY_WIN,
                    homeFavored ? odds.home() : odds.away()));
        }

        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::odds)
                        .thenComparing(Candidate::resultId))
                .toList();

        List<PickSlip> slips = new ArrayList<>();
        for (int from = 0; from + input.combinedN() <= sorted.size(); from += input.combinedN()) {
            List<PickSelection> selections = sorted.subList(from, from + input.combinedN()).stream()
                    .map(c -> new PickSelection(c.resultId(), c.predicted()))
                    .toList();
            slips.add(new PickSlip(selections));
        }
        return slips;
    }
}
