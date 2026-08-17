package com.toto.baseballApi.pick.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * Which games the pick engine is allowed to see — one definition, shared by the persisting
 * simulation and the research backtest.
 *
 * <p>If the two disagreed on the eligible tournaments or the game types, a parameter search would
 * be tuning against a different universe than the one the simulation later bets on, and every
 * number it produced would be off in a way nothing would flag.
 */
public final class PickUniverse {

    /** Tournaments eligible for picks. */
    public static final List<String> TOURNAMENTS = List.of("KBO", "NPB", "MLB");

    private static final String MLB = "MLB";

    /**
     * Which games may be combined onto one slip. A 프로토 승부식 구매권 cannot mix time slots: MLB runs
     * in the Korean morning while KBO and NPB run in the evening, so {@code {MLB}} and
     * {@code {KBO, NPB}} are two separate combination buckets.
     *
     * <p>This is also the staking unit. Because the MLB slot resolves before the KBO/NPB slot of the
     * same ymd, a path-dependent stake rule gets two ordered observations per day rather than one —
     * see {@code MartingaleStaking}.
     */
    public static String combinationBucket(String tournament) {
        return MLB.equals(tournament) ? MLB : "KBO_NPB";
    }

    /**
     * One row per physical game. The same fixture is routinely sold in two consecutive 회차 at
     * slightly different prices — e.g. 260422 10:45 샌프란시스코 vs LA다저스 exists as id 11120 (46회차,
     * 1.72) and id 10721 (47회차, 1.67). Grouping games by anything wider than 회차 therefore has to
     * collapse them, or a "2게임 조합" can come out as the same game twice: two perfectly correlated
     * legs priced as if independent, which is neither purchasable nor a parlay.
     *
     * <p>Keyed on the game time as well as the teams, so a genuine doubleheader stays two games.
     * When both copies survive that, the earlier 회차 wins — an arbitrary but fixed rule, chosen over
     * anything price-aware so the backtest cannot quietly pick the better of two prices.
     */
    public static List<BaseballResult> distinctFixtures(List<BaseballResult> games) {
        record FixtureKey(String tournament, String ymd, String tm, String home, String away) {
        }
        Map<FixtureKey, BaseballResult> byFixture = new LinkedHashMap<>();
        for (BaseballResult game : games) {
            FixtureKey key = new FixtureKey(
                    game.tournament(), game.ymd(), game.tm(), game.home(), game.away());
            byFixture.merge(key, game, PickUniverse::preferredListing);
        }
        return List.copyOf(byFixture.values());
    }

    private static BaseballResult preferredListing(BaseballResult kept, BaseballResult candidate) {
        if ((kept.publishedOdds() == null) != (candidate.publishedOdds() == null)) {
            return kept.publishedOdds() != null ? kept : candidate;
        }
        int byRound = Integer.compare(kept.round(), candidate.round());
        if (byRound != 0) {
            return byRound < 0 ? kept : candidate;
        }
        return kept.id() <= candidate.id() ? kept : candidate;
    }

    /** The market picks are placed in — home/draw/away, so a draw is a distinct outcome. */
    public static final String THREE_WAY_GAME_TYPE = "야구 승1패";

    /** The market team form is measured from — no draw, so a win rate is well defined. */
    public static final String TWO_WAY_GAME_TYPE = "야구 승패";

    private PickUniverse() {
    }
}
