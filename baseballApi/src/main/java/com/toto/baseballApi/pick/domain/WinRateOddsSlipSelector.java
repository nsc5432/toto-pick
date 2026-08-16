package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

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
 */
public class WinRateOddsSlipSelector {

    private static final String MLB = "MLB";
    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";
    private static final int RANK_LIMIT = 5;

    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        TeamRankSets rankSets = rankTeams(input.historyGames(), input.num());

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

    private record Appearance(String ymd, String tm, boolean win) {
    }

    private record TeamRankSets(Set<String> top, Set<String> bottom) {
    }

    private record Candidate(Integer resultId, String predictedTotalResult, double odds) {
    }

    private TeamRankSets rankTeams(List<BaseballResult> historyGames, int num) {
        Map<String, List<Appearance>> appearancesByTeam = new HashMap<>();
        for (BaseballResult game : historyGames) {
            appearancesByTeam.computeIfAbsent(game.home(), k -> new ArrayList<>())
                    .add(new Appearance(game.ymd(), game.tm(), HOME_WIN.equals(game.totalResult())));
            appearancesByTeam.computeIfAbsent(game.away(), k -> new ArrayList<>())
                    .add(new Appearance(game.ymd(), game.tm(), AWAY_WIN.equals(game.totalResult())));
        }

        Map<String, Long> percentByTeam = new HashMap<>();
        Comparator<Appearance> mostRecentFirst = Comparator.comparing(Appearance::ymd)
                .thenComparing(Appearance::tm)
                .reversed();
        appearancesByTeam.forEach((team, appearances) -> {
            List<Appearance> recent = appearances.stream().sorted(mostRecentFirst).limit(num).toList();
            long wins = recent.stream().filter(Appearance::win).count();
            percentByTeam.put(team, Math.round(wins * 100.0 / recent.size()));
        });

        List<Long> distinctDesc = percentByTeam.values().stream().distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        Set<Long> topPercents = new HashSet<>(distinctDesc.subList(0, Math.min(RANK_LIMIT, distinctDesc.size())));
        Set<Long> bottomPercents = new HashSet<>(
                distinctDesc.subList(Math.max(0, distinctDesc.size() - RANK_LIMIT), distinctDesc.size()));

        Set<String> top = new HashSet<>();
        Set<String> bottom = new HashSet<>();
        percentByTeam.forEach((team, percent) -> {
            if (topPercents.contains(percent)) {
                top.add(team);
            }
            if (bottomPercents.contains(percent)) {
                bottom.add(team);
            }
        });
        return new TeamRankSets(top, bottom);
    }

    private Candidate toCandidate(BaseballResult game, TeamRankSets rankSets, double x, double y) {
        Set<String> predictions = new LinkedHashSet<>();
        collectPredictions(predictions, rankSets, game.home(), game.homeDiv(), true, x, y);
        collectPredictions(predictions, rankSets, game.away(), game.awayDiv(), false, x, y);
        if (predictions.size() != 1) {
            // No signal, or the two teams' signals contradict each other — skip the game.
            return null;
        }

        String predicted = predictions.iterator().next();
        Double predictedSideOdds = HOME_WIN.equals(predicted) ? game.homeDiv() : game.awayDiv();
        if (predictedSideOdds == null) {
            return null;
        }
        return new Candidate(game.id(), predicted, predictedSideOdds);
    }

    private void collectPredictions(
            Set<String> predictions, TeamRankSets rankSets,
            String team, Double winOdds, boolean isHome, double x, double y) {
        if (winOdds == null) {
            return;
        }
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
