package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;

/**
 * Win-rate vs. odds mismatch strategy over 3-way ("야구 승1패") games.
 *
 * <p>Each team's win rate is computed from its {@code num} most recent 2-way ("야구 승패") games
 * before the target ymd, pooled across tournaments. DENSE_RANK over the rounded percentage yields
 * a top-5 and bottom-5 set (ties may widen the sets, matching SQL DENSE_RANK semantics). A game
 * becomes a candidate when a top-set team's win odds is {@code >= x} (back the strong team) or a
 * bottom-set team's win odds is {@code <= y} (fade the overvalued weak team — bet its opponent).
 * A draw ("1") is never predicted; games with contradictory signals are skipped.
 *
 * <p>Candidates are grouped (MLB alone; KBO+NPB together), sorted by the predicted side's odds
 * ascending, and chunked into slips of exactly {@code combinedN}; the trailing incomplete chunk —
 * the highest-odds leftovers — is discarded.
 *
 * <p>All four knobs are tunable: the window length and both odds thresholds decide how selective
 * the filter is, and {@code combinedN} trades hit rate against payout. Which combination actually
 * earns is an empirical question, which is what the search pipeline is for.
 */
public class WinRateOddsSlipSelector implements TunableAlgorithm {

    public static final String CODE = "WIN_RATE_ODDS";

    private static final String MLB = "MLB";
    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "승률-배당 괴리";
    }

    @Override
    public ParamSpace paramSpace() {
        return ParamSpace.of(
                // Window length: short enough to track form, long enough not to be one hot streak.
                new ParamSpec(AlgorithmParams.NUM, 5, 40, 5, 20),
                // Back a strong team only while the market still pays at least x for it.
                new ParamSpec(AlgorithmParams.X, 1.2, 3.0, 0.2, 1.8),
                // Fade a weak team only while the market prices it at or under y.
                new ParamSpec(AlgorithmParams.Y, 1.4, 5.0, 0.2, 2.4),
                // How many DENSE_RANK positions count as top/bottom. Was a hard-coded 5 — the one
                // threshold in this family that was never swept, and the strongest lever on sample
                // size, since it decides how many teams can produce a candidate at all.
                new ParamSpec(TeamRankSets.RANK_LIMIT, 3, 15, 3, TeamRankSets.DEFAULT_RANK_LIMIT),
                // Legs per slip: more legs multiply the payout and shrink the hit rate — capped at
                // 3 by 설계 결정 D1, since each extra leg raises the per-leg edge the goal demands.
                new ParamSpec(AlgorithmParams.COMBINED_N, 2, 3, 1, 3));
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        TeamRankSets rankSets = TeamRankSets.of(input.formIndex(), input.ymd(), input.num(),
                input.params().getInt(TeamRankSets.RANK_LIMIT, (int) TeamRankSets.DEFAULT_RANK_LIMIT));

        List<Candidate> mlbCandidates = new ArrayList<>();
        List<Candidate> kboNpbCandidates = new ArrayList<>();
        for (BaseballResult game : input.dayGames()) {
            Candidate candidate = toCandidate(game, rankSets, input.x(), input.y());
            if (candidate == null) {
                continue;
            }
            (MLB.equals(game.tournament()) ? mlbCandidates : kboNpbCandidates).add(candidate);
        }

        List<PickSlip> slips = new ArrayList<>();
        slips.addAll(chunkIntoSlips(mlbCandidates, input.combinedN()));
        slips.addAll(chunkIntoSlips(kboNpbCandidates, input.combinedN()));
        return slips;
    }

    private record Candidate(Integer resultId, String predictedTotalResult, double odds) {
    }

    private Candidate toCandidate(BaseballResult game, TeamRankSets rankSets, double x, double y) {
        ThreeWayOdds odds = game.publishedOdds();
        if (odds == null) {
            return null;
        }

        Set<String> predictions = new LinkedHashSet<>();
        collectPredictions(predictions, rankSets, game.home(), odds.home(), true, x, y);
        collectPredictions(predictions, rankSets, game.away(), odds.away(), false, x, y);
        if (predictions.size() != 1) {
            // No signal, or the two teams' signals contradict each other — skip the game.
            return null;
        }

        String predicted = predictions.iterator().next();
        return new Candidate(
                game.id(), predicted, HOME_WIN.equals(predicted) ? odds.home() : odds.away());
    }

    private void collectPredictions(
            Set<String> predictions, TeamRankSets rankSets,
            String team, double winOdds, boolean isHome, double x, double y) {
        if (rankSets.top().contains(team) && winOdds >= x) {
            predictions.add(isHome ? HOME_WIN : AWAY_WIN);
        }
        if (rankSets.bottom().contains(team) && winOdds <= y) {
            predictions.add(isHome ? AWAY_WIN : HOME_WIN);
        }
    }

    private List<PickSlip> chunkIntoSlips(List<Candidate> candidates, int combinedN) {
        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::odds)
                        .thenComparing(Candidate::resultId))
                .toList();

        List<PickSlip> slips = new ArrayList<>();
        for (int from = 0; from + combinedN <= sorted.size(); from += combinedN) {
            List<PickSelection> selections = sorted.subList(from, from + combinedN).stream()
                    .map(c -> new PickSelection(c.resultId(), c.predictedTotalResult()))
                    .toList();
            slips.add(new PickSlip(selections));
        }
        return slips;
    }
}
