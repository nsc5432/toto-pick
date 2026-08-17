package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOddsEstimator;

/**
 * {@link WinRateOddsSlipSelector} moved to the 2-way ("야구 승패") market.
 *
 * <p>The selection rule is unchanged and shares its ranking with the 3-way version via
 * {@link TeamRankSets}: back a top-form team while the market still pays at least {@code x} for it,
 * fade a bottom-form team while it is priced at or under {@code y}, skip games where the two teams'
 * signals contradict. What changes is the market the price is read from and the bet is settled in —
 * a one-run win is a hit here rather than a loss.
 *
 * <p><strong>Why this family and not another.</strong> Of every family tried, {@code WIN_RATE_ODDS}
 * is the only one whose leg excess return came out positive in <em>both</em> the train and validation
 * windows (docs/statistical-model-design.md §12: +7.0%p per leg, t ≈ 1.90 — suggestive, still short of
 * the t ≥ 2.0 bar). The 승패 switch is worth about +6%p per leg on its own (§13), and it is
 * independent of whatever edge the form filter has, since one changes the settlement market and the
 * other changes which side is backed. Putting them together is the first candidate in this project
 * with two separately-motivated reasons to be positive.
 *
 * <p><strong>{@code x} and {@code y} do not mean here what they mean there.</strong> Both are odds
 * thresholds, and 2-way prices are systematically shorter than 3-way ones — measured over the 2,611
 * published games, the 5th–95th percentile band moves from 1.78–3.65 down to 1.38–2.19. Reusing the
 * 3-way sweep range would put every threshold outside the data, so the range is shifted to match the
 * distribution rather than the parameter name. This is the reason the thresholds read the estimated
 * 2-way price and not the 3-way one: the question the rule asks is whether the price being taken is
 * long enough, and the price being taken is the 2-way one.
 *
 * <p>Pricing, pairing, and settlement work exactly as in {@link FavoriteTwoWaySlipAlgorithm}: the
 * signal comes from the paired 3-way row's published odds (the 승패 row has no {@code PUB_*}), the leg
 * carries its estimated {@link BackedPrice}, and the pick points at the 승패 row so settlement pays
 * that market's {@code TOTAL_DIV}. The 승패 row's own {@code TOTAL_DIV} is never a signal — only its
 * realized side is measured, which is 설계 결정 D2.
 */
public class WinRateOddsTwoWaySlipSelector implements TunableAlgorithm {

    public static final String CODE = "WIN_RATE_ODDS_2WAY";

    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "승률-배당 괴리(승패)";
    }

    @Override
    public ParamSpace paramSpace() {
        return ParamSpace.of(
                // Same window range as the 3-way version — form is form, the market did not change it.
                new ParamSpec(AlgorithmParams.NUM, 5, 40, 5, 20),
                // Shifted to the 2-way price distribution (p05–p95 ≈ 1.38–2.19, pooled p01–p99
                // 1.31–2.67), not copied from the 3-way range where it would never bind.
                new ParamSpec(AlgorithmParams.X, 1.0, 2.6, 0.2, 1.4),
                new ParamSpec(AlgorithmParams.Y, 1.2, 4.0, 0.4, 1.8),
                // See WinRateOddsSlipSelector: was a hard-coded 5, and is the strongest lever this
                // family has on sample size. The validation window holds at most 628 fixtures, so
                // how far a filter can be opened is the whole question of whether t can ever grow.
                new ParamSpec(TeamRankSets.RANK_LIMIT, 3, 15, 3, TeamRankSets.DEFAULT_RANK_LIMIT),
                new ParamSpec(AlgorithmParams.COMBINED_N, 2, 3, 1, 3),
                // Deliberately narrower than FAVORITE_2WAY's 0.40–0.80. This family already spends
                // its grid on num/x/y, and the share is not a strategy threshold — it is a price
                // model parameter with independent evidence: mean price error against published 승패
                // odds is 1.60% at 0.60 and rises steeply either side (2.70% / 3.17%). Sweeping it
                // across nine points here would multiply the grid by 9 against the 3-way version it
                // must be compared with, buying sparser coverage of the knobs that matter.
                new ParamSpec(FavoriteTwoWaySlipAlgorithm.ONE_RUN_HOME_SHARE, 0.55, 0.65, 0.05,
                        TwoWayOddsEstimator.DEFAULT_ONE_RUN_HOME_SHARE));
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        TeamRankSets rankSets = TeamRankSets.of(input.formIndex(), input.ymd(), input.num(),
                input.params().getInt(TeamRankSets.RANK_LIMIT, (int) TeamRankSets.DEFAULT_RANK_LIMIT));
        double oneRunHomeShare = input.params().get(
                FavoriteTwoWaySlipAlgorithm.ONE_RUN_HOME_SHARE,
                TwoWayOddsEstimator.DEFAULT_ONE_RUN_HOME_SHARE);

        // Grouped by 조합버킷: a 프로토 구매권 cannot mix time slots, so MLB (Korean morning) and
        // KBO+NPB (evening) never share a slip.
        Map<String, List<Candidate>> byBucket = new LinkedHashMap<>();
        for (BaseballResult game : input.dayGames()) {
            Candidate candidate = toCandidate(
                    game, input.marketPairs(), rankSets, input.x(), input.y(), oneRunHomeShare);
            if (candidate == null) {
                continue;
            }
            byBucket.computeIfAbsent(
                            PickUniverse.combinationBucket(game.tournament()), key -> new ArrayList<>())
                    .add(candidate);
        }

        List<PickSlip> slips = new ArrayList<>();
        for (List<Candidate> candidates : byBucket.values()) {
            slips.addAll(chunkIntoSlips(candidates, input.combinedN()));
        }
        return slips;
    }

    private record Candidate(Integer resultId, String predictedTotalResult, BackedPrice price) {
    }

    private Candidate toCandidate(
            BaseballResult game, MarketPairIndex marketPairs, TeamRankSets rankSets,
            double x, double y, double oneRunHomeShare) {
        ThreeWayOdds published = game.publishedOdds();
        if (published == null) {
            return null;
        }
        BaseballResult twoWayGame = marketPairs.pairOf(game);
        if (twoWayGame == null) {
            return null;
        }
        TwoWayOdds odds = TwoWayOddsEstimator.from(published, oneRunHomeShare);
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
        double backed = HOME_WIN.equals(predicted) ? odds.home() : odds.away();
        return new Candidate(
                twoWayGame.id(), predicted, new BackedPrice(backed, odds.overround()));
    }

    private void collectPredictions(
            Set<String> predictions, TeamRankSets rankSets,
            String team, double winOdds, boolean isHome, double x, double y) {
        if (rankSets.isTop(team) && winOdds >= x) {
            predictions.add(isHome ? HOME_WIN : AWAY_WIN);
        }
        if (rankSets.isBottom(team) && winOdds <= y) {
            predictions.add(isHome ? AWAY_WIN : HOME_WIN);
        }
    }

    private List<PickSlip> chunkIntoSlips(List<Candidate> candidates, int combinedN) {
        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.price().odds())
                        .thenComparing(Candidate::resultId))
                .toList();

        List<PickSlip> slips = new ArrayList<>();
        for (int from = 0; from + combinedN <= sorted.size(); from += combinedN) {
            List<PickSelection> selections = sorted.subList(from, from + combinedN).stream()
                    .map(c -> new PickSelection(c.resultId(), c.predictedTotalResult(), c.price()))
                    .toList();
            slips.add(new PickSlip(selections));
        }
        return slips;
    }
}
