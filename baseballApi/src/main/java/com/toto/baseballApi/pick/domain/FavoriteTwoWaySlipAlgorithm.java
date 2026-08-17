package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.TwoWayOdds;

/**
 * {@link FavoriteOddsSlipAlgorithm} moved to the 2-way ("야구 승패") market: same market-consensus
 * selection, settled where a one-run win still counts.
 *
 * <p><strong>Why the market matters more than the selection here.</strong> In 승1패 the slots are
 * "홈 2점차 이상 승 / 1점차 경기 / 원정 2점차 이상 승", and the market prices that middle slot at
 * 22–28%. A favorite backer loses every one-run game, which is why the 2-leg hit rate sits near 0.20
 * even though the picks themselves are the market's own. There is no room to fix that by demanding
 * shorter prices: of 2,697 published games only a handful have any side under 1.50. The 승패 market
 * simply does not have the middle slot, so the same picks convert at roughly 0.58 a leg instead of
 * 0.45. Its margin is also lower — 14.1% against 15.2% — so the no-skill leg return improves from
 * −13.2% to −12.4%.
 *
 * <p><strong>This is a market switch, not a new signal.</strong> Both markets are read straight off
 * their own published prices, so this algorithm backs whichever side 승패 itself calls the favorite.
 * That is usually — but not always — the side {@code FAVORITE} backs: the two markets disagree on the
 * favorite in about 14% of games, because the outright market prices home advantage in the one-run
 * games that 승1패 sets aside. Nothing here claims to know something the odds do not, and the
 * excess-return numbers should say so.
 *
 * <p>Selection and settlement both live on the 승패 row: it carries its own {@code PUB_HOME_DIV} /
 * {@code PUB_AWAY_DIV}, and the pick points at it so settlement resolves that market's
 * {@code TOTAL_RESULT} and pays its {@code TOTAL_DIV}. The row's own {@code TOTAL_DIV} is never read
 * as a signal — only the realized side is measured there, which is exactly the bias 설계 결정 D2
 * rejected. {@link MarketPairIndex} is still what finds the row, because the day and slot groupings
 * are built on the 승1패 universe so that the two families bet the same fixtures.
 *
 * <p>Games are skipped when they have no 승패 counterpart or the counterpart carries no published
 * price. Both tests are outcome-blind, as they must be — filtering on the 승패 row's result instead
 * would be lookahead.
 */
public class FavoriteTwoWaySlipAlgorithm implements TunableAlgorithm {

    public static final String CODE = "FAVORITE_2WAY";

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
                // 1~3 legs (설계 결정 D1, 2026-08-17 revision), matching FAVORITE so the two stay
                // comparable. The only knob this family has: selection is the market's own opinion,
                // so there is nothing else here to tune. `oneRunHomeShare` used to sit alongside it,
                // sizing the estimator that stood in for the missing 승패 price; the published price
                // replaced it.
                new ParamSpec(AlgorithmParams.COMBINED_N, 1, 3, 1, 3));
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        record Candidate(Integer resultId, String predicted, BackedPrice price) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (BaseballResult game : input.dayGames()) {
            BaseballResult twoWayGame = input.marketPairs().pairOf(game);
            if (twoWayGame == null) {
                continue;
            }
            TwoWayOdds odds = twoWayGame.publishedTwoWayOdds();
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
