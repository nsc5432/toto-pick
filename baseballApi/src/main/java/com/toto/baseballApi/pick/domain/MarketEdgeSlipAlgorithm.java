package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;

/**
 * Value strategy over 3-way ("야구 승1패") games: bet only where recent form disagrees with the
 * market by more than a stated margin.
 *
 * <p>A different family from the other two. {@code FAVORITE} follows the market and
 * {@code WIN_RATE_ODDS} filters on rank plus a raw odds threshold; this one converts both sides to
 * probabilities and bets the difference. Odds are first stripped of the bookmaker's margin (see
 * {@link ThreeWayOdds}), so what remains is the market's actual implied probability. Form supplies a
 * second estimate, and a side is backed only when its own estimate exceeds the market's by
 * {@code edgeThreshold}.
 *
 * <p>Comparing probabilities rather than raw odds is the substantive difference: a 2.0 shot is
 * cheap or expensive depending entirely on who is playing, which a fixed odds cutoff cannot see.
 *
 * <p>{@code minOdds} floors the price because the edge estimate is noisy and a heavy favourite pays
 * too little to survive being wrong occasionally. A draw is never predicted; the model splits only
 * the non-draw probability mass, leaving the market's draw estimate untouched.
 *
 * <p>Prices come from {@link BaseballResult#publishedOdds()} — the odds as actually posted before
 * the game. The backfilled {@code *_DIV} columns must never feed a comparison like this one: only
 * the realized side of those holds a measured price, so an "edge" computed from them partly encodes
 * the result (docs/statistical-model-design.md, 설계 결정 D2).
 */
public class MarketEdgeSlipAlgorithm implements TunableAlgorithm {

    public static final String CODE = "MARKET_EDGE";

    /** Minimum probability advantage over the de-vigged market price before a side is backed. */
    public static final String EDGE_THRESHOLD = "edgeThreshold";
    /** Price floor — below this, being right often enough stops paying for being wrong. */
    public static final String MIN_ODDS = "minOdds";
    /** How hard the form estimate is pulled back toward an even 50/50 split. */
    public static final String SHRINK_STRENGTH = "shrinkStrength";

    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "시장 괴리(밸류)";
    }

    /**
     * Grid: 4 × 2 × 5 × 3 × 4 = 480 points, exhaustive under a 480+ budget.
     *
     * <p>{@code combinedN} stops at 3 by 설계 결정 D1: each extra leg multiplies the margin the slip
     * has to overcome, so 4- and 5-leg slips need a per-leg edge that has never been demonstrated
     * here. Sweeping them spends most of the budget on candidates that cannot clear the goal.
     */
    @Override
    public ParamSpace paramSpace() {
        return ParamSpace.of(
                new ParamSpec(AlgorithmParams.NUM, 10, 40, 10, 20),
                new ParamSpec(AlgorithmParams.COMBINED_N, 2, 3, 1, 3),
                new ParamSpec(EDGE_THRESHOLD, 0.02, 0.14, 0.03, 0.05),
                new ParamSpec(MIN_ODDS, 1.4, 2.2, 0.4, 1.8),
                // 0 = raw form; 45 pulls a 100-vs-0 pairing back to roughly 0.61.
                new ParamSpec(SHRINK_STRENGTH, 0, 45, 15, 15));
    }

    private record Candidate(Integer resultId, String predicted, double edge, double odds) {
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        double edgeThreshold = input.params().get(EDGE_THRESHOLD, 0.05);
        double minOdds = input.params().get(MIN_ODDS, 1.8);
        double shrinkStrength = input.params().get(SHRINK_STRENGTH, 15);

        List<Candidate> candidates = new ArrayList<>();
        for (BaseballResult game : input.dayGames()) {
            Candidate candidate = toCandidate(game, input, edgeThreshold, minOdds, shrinkStrength);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        // Best-edge legs fill the earliest slips; the trailing remainder — the weakest signals —
        // is dropped rather than padded out into a slip that only dilutes the rest.
        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::edge).reversed()
                        .thenComparing(Candidate::resultId))
                .toList();

        List<PickSlip> slips = new ArrayList<>();
        int combinedN = input.combinedN();
        for (int from = 0; from + combinedN <= sorted.size(); from += combinedN) {
            slips.add(new PickSlip(sorted.subList(from, from + combinedN).stream()
                    .map(c -> new PickSelection(c.resultId(), c.predicted()))
                    .toList()));
        }
        return slips;
    }

    private Candidate toCandidate(
            BaseballResult game, SlipSelectionInput input,
            double edgeThreshold, double minOdds, double shrinkStrength) {

        ThreeWayOdds odds = game.publishedOdds();
        if (odds == null) {
            return null;
        }
        Long homeForm = input.formIndex().winPercent(game.home(), input.ymd(), input.num());
        Long awayForm = input.formIndex().winPercent(game.away(), input.ymd(), input.num());
        if (homeForm == null || awayForm == null) {
            return null;
        }

        double modelHomeShare = shrunkHomeShare(homeForm, awayForm, shrinkStrength);
        double decisive = odds.impliedDecisive();

        double homeEdge = modelHomeShare * decisive - odds.impliedHome();
        double awayEdge = (1.0 - modelHomeShare) * decisive - odds.impliedAway();

        boolean backHome = homeEdge >= awayEdge;
        double edge = backHome ? homeEdge : awayEdge;
        double price = backHome ? odds.home() : odds.away();

        if (edge < edgeThreshold || price < minOdds) {
            return null;
        }
        return new Candidate(game.id(), backHome ? HOME_WIN : AWAY_WIN, edge, price);
    }

    /**
     * The home side's share of the decisive probability mass, shrunk toward 0.5 (M2).
     *
     * <p>A win percentage over a short window is a small-sample estimate: 20 games can read 100%
     * without the team being certain to win the next one. Adding {@code shrinkStrength} to both
     * sides pulls the split back toward even, by more when the strength is large — the standard
     * Beta-Binomial prior, applied on the 0..100 percentage scale {@code winPercent} returns. At 0
     * the raw form passes through; two winless teams still give 50/50 rather than 0/0.
     *
     * <p>It is a swept knob rather than a constant because how much to distrust recent form is an
     * empirical question, and a constant is a value that never gets validated.
     */
    private double shrunkHomeShare(long homeForm, long awayForm, double shrinkStrength) {
        double homeWeight = homeForm + shrinkStrength;
        double awayWeight = awayForm + shrinkStrength;
        if (homeWeight + awayWeight <= 0) {
            return 0.5;
        }
        return homeWeight / (homeWeight + awayWeight);
    }
}
