package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOddsEstimator;

/**
 * {@link FavoriteOddsSlipAlgorithm} moved to the 2-way ("야구 승패") market: same market-consensus
 * selection, settled where a one-run win still counts.
 *
 * <p><strong>Why the market matters more than the selection here.</strong> In 승1패 the slots are
 * "홈 2점차 이상 승 / 1점차 경기 / 원정 2점차 이상 승", and the market prices that middle slot at
 * 22–28%. A favorite backer loses every one-run game, which is why the 2-leg hit rate sits near 0.20
 * even though the picks themselves are the market's own. There is no room to fix that by demanding
 * shorter prices: of 2,611 published games only 10 have any side under 1.50. The 승패 market simply
 * does not have the middle slot, so the same picks convert at roughly 0.58 a leg instead of 0.45.
 * Its margin is also lower — 13.6% against 15.2% — so the no-skill leg return improves from −13.2%
 * to −12.0%.
 *
 * <p><strong>This is a market switch, not a new signal.</strong> At the default
 * {@code oneRunHomeShare = 0.5} the estimator preserves the probability gap between the two sides, so
 * the favorite it backs is always the same team {@code FAVORITE} would back. That is deliberate: it
 * isolates the effect of changing markets from any change in selection. Nothing here claims to know
 * something the odds do not, and the excess-return numbers should say so.
 *
 * <p>Selection reads the published 3-way price (the 승패 row has no {@code PUB_*} columns of its own)
 * and prices the leg through {@link TwoWayOddsEstimator}; the pick itself points at the 승패 row, so
 * settlement resolves against that market's {@code TOTAL_RESULT} and pays its {@code TOTAL_DIV}. The
 * row's own {@code TOTAL_DIV} is never read as a signal — only the realized side is measured there,
 * which is exactly the bias 설계 결정 D2 rejected.
 *
 * <p>Games are skipped when they have no published 3-way price, no 승패 counterpart, or a price the
 * estimator cannot quote. All three tests are outcome-blind, as they must be — filtering on the 승패
 * row's result instead would be lookahead.
 */
public class FavoriteTwoWaySlipAlgorithm implements TunableAlgorithm {

    public static final String CODE = "FAVORITE_2WAY";

    /** 1점차 경기 중 홈이 이긴 비율 — 3면 배당에서 2면 가격을 유추할 때의 분배 비율. */
    public static final String ONE_RUN_HOME_SHARE = "oneRunHomeShare";

    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "배당 우세팀(승패)";
    }

    @Override
    public ParamSpace paramSpace() {
        return ParamSpace.of(
                // 2~3 legs only (설계 결정 D1), matching FAVORITE so the two are comparable.
                new ParamSpec(AlgorithmParams.COMBINED_N, 2, 3, 1, 3),
                // Centred on the price-calibrated 0.60 rather than the coin-flip 0.5, but still
                // swept: the calibration fixes a price model, and whether that same value is the
                // best one to *bet* on is a separate question only the sweep can answer.
                new ParamSpec(ONE_RUN_HOME_SHARE, 0.40, 0.80, 0.05,
                        TwoWayOddsEstimator.DEFAULT_ONE_RUN_HOME_SHARE));
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        record Candidate(Integer resultId, String predicted, BackedPrice price) {
        }

        double oneRunHomeShare = input.params().get(
                ONE_RUN_HOME_SHARE, TwoWayOddsEstimator.DEFAULT_ONE_RUN_HOME_SHARE);

        List<Candidate> candidates = new ArrayList<>();
        for (BaseballResult game : input.dayGames()) {
            ThreeWayOdds published = game.publishedOdds();
            if (published == null) {
                continue;
            }
            BaseballResult twoWayGame = input.marketPairs().pairOf(game);
            if (twoWayGame == null) {
                continue;
            }
            TwoWayOdds odds = TwoWayOddsEstimator.from(published, oneRunHomeShare);
            if (odds == null || odds.home() == odds.away()) {
                continue;
            }
            boolean homeFavored = odds.home() < odds.away();
            candidates.add(new Candidate(
                    twoWayGame.id(),
                    homeFavored ? HOME_WIN : AWAY_WIN,
                    new BackedPrice(
                            homeFavored ? odds.home() : odds.away(), odds.overround())));
        }

        List<Candidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.price().odds())
                        .thenComparing(Candidate::resultId))
                .toList();

        List<PickSlip> slips = new ArrayList<>();
        for (int from = 0; from + input.combinedN() <= sorted.size(); from += input.combinedN()) {
            List<PickSelection> selections = sorted.subList(from, from + input.combinedN()).stream()
                    .map(c -> new PickSelection(c.resultId(), c.predicted(), c.price()))
                    .toList();
            slips.add(new PickSlip(selections));
        }
        return slips;
    }
}
