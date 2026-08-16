package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * Favorite-backing baseline strategy over 3-way ("야구 승1패") games: for every day game with both
 * home and away win odds, back whichever side the market favors (the lower odds). Candidates are
 * sorted by the predicted side's odds ascending and chunked into slips of exactly
 * {@code combinedN}; the trailing incomplete chunk is discarded. Ignores {@code num}/{@code x}/
 * {@code y} — it needs no history. Exists as the market-consensus baseline the other algorithms
 * have to beat.
 */
public class FavoriteOddsSlipAlgorithm implements PickAlgorithm {

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
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        record Candidate(Integer resultId, String predicted, double odds) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (BaseballResult game : input.dayGames()) {
            if (game.homeDiv() == null || game.awayDiv() == null || game.homeDiv().equals(game.awayDiv())) {
                continue;
            }
            boolean homeFavored = game.homeDiv() < game.awayDiv();
            candidates.add(new Candidate(
                    game.id(),
                    homeFavored ? HOME_WIN : AWAY_WIN,
                    homeFavored ? game.homeDiv() : game.awayDiv()));
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
