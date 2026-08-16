package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * Value strategy over 3-way ("야구 승1패") games: bet only where recent form disagrees with the
 * market by more than a stated margin.
 *
 * <p>A different family from the other two. {@code FAVORITE} follows the market and
 * {@code WIN_RATE_ODDS} filters on rank plus a raw odds threshold; this one converts both sides to
 * probabilities and bets the difference. Odds are first stripped of the bookmaker's margin — the
 * three inverse odds sum to more than 1, and that excess is the house's cut, not information — so
 * what remains is the market's actual implied probability. Form supplies a second estimate, and a
 * side is backed only when its own estimate exceeds the market's by {@code edgeThreshold}.
 *
 * <p>Comparing probabilities rather than raw odds is the substantive difference: a 2.0 shot is
 * cheap or expensive depending entirely on who is playing, which a fixed odds cutoff cannot see.
 *
 * <p>{@code minOdds} floors the price because the edge estimate is noisy and a heavy favourite pays
 * too little to survive being wrong occasionally. A draw is never predicted; the model splits only
 * the non-draw probability mass, leaving the market's draw estimate untouched.
 */
public class MarketEdgeSlipAlgorithm implements TunableAlgorithm {

    public static final String CODE = "MARKET_EDGE";

    /** Minimum probability advantage over the de-vigged market price before a side is backed. */
    public static final String EDGE_THRESHOLD = "edgeThreshold";
    /** Price floor — below this, being right often enough stops paying for being wrong. */
    public static final String MIN_ODDS = "minOdds";

    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";

    /**
     * Added to both teams' win percentages so two winless teams give 50/50 rather than 0/0, and a
     * single 100%-vs-0% pairing is not read as absolute certainty.
     */
    private static final double FORM_SMOOTHING = 5.0;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "시장 괴리(밸류)";
    }

    @Override
    public ParamSpace paramSpace() {
        return ParamSpace.of(
                new ParamSpec(AlgorithmParams.NUM, 10, 40, 5, 20),
                new ParamSpec(AlgorithmParams.COMBINED_N, 2, 5, 1, 3),
                new ParamSpec(EDGE_THRESHOLD, 0.02, 0.20, 0.02, 0.06),
                new ParamSpec(MIN_ODDS, 1.2, 2.4, 0.2, 1.6));
    }

    private record Candidate(Integer resultId, String predicted, double edge, double odds) {
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        double edgeThreshold = input.params().get(EDGE_THRESHOLD, 0.06);
        double minOdds = input.params().get(MIN_ODDS, 1.6);

        List<Candidate> candidates = new ArrayList<>();
        for (BaseballResult game : input.dayGames()) {
            Candidate candidate = toCandidate(game, input, edgeThreshold, minOdds);
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
            BaseballResult game, SlipSelectionInput input, double edgeThreshold, double minOdds) {

        if (game.homeDiv() == null || game.drawDiv() == null || game.awayDiv() == null) {
            return null;
        }
        Long homeForm = input.formIndex().winPercent(game.home(), input.ymd(), input.num());
        Long awayForm = input.formIndex().winPercent(game.away(), input.ymd(), input.num());
        if (homeForm == null || awayForm == null) {
            return null;
        }

        // De-vig: the three inverse odds overshoot 1 by the house margin, so rescale to sum to 1.
        double invHome = 1.0 / game.homeDiv();
        double invDraw = 1.0 / game.drawDiv();
        double invAway = 1.0 / game.awayDiv();
        double overround = invHome + invDraw + invAway;
        double marketHome = invHome / overround;
        double marketAway = invAway / overround;
        double decisive = 1.0 - (invDraw / overround);

        double homeWeight = homeForm + FORM_SMOOTHING;
        double awayWeight = awayForm + FORM_SMOOTHING;
        double modelHomeShare = homeWeight / (homeWeight + awayWeight);

        double homeEdge = modelHomeShare * decisive - marketHome;
        double awayEdge = (1.0 - modelHomeShare) * decisive - marketAway;

        boolean backHome = homeEdge >= awayEdge;
        double edge = backHome ? homeEdge : awayEdge;
        double odds = backHome ? game.homeDiv() : game.awayDiv();

        if (edge < edgeThreshold || odds < minOdds) {
            return null;
        }
        return new Candidate(game.id(), backHome ? HOME_WIN : AWAY_WIN, edge, odds);
    }
}
