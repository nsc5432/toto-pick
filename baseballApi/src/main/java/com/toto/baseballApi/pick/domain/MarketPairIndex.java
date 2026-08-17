package com.toto.baseballApi.pick.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.FixtureKey;

/**
 * Pairs each 3-way ("야구 승1패") row with the 2-way ("야구 승패") row for the same fixture.
 *
 * <p>The two markets list the same games under separate rows, and an algorithm that bets the 2-way
 * market needs both: the 3-way row is the only one carrying a published pre-game price, while the
 * 2-way row is the one settlement must resolve against. This index is the bridge.
 *
 * <p>Built once per run and shared across days, for the same reason {@link TeamFormIndex} is: a sweep
 * of hundreds of candidates over hundreds of days is only feasible if per-day work stays out of the
 * inner loop. It is safe to build from the whole history because it maps identity, not results — no
 * lookup depends on a date, so there is nothing here for a day to see past.
 *
 * <p>The key is the fixture identity {@link FixtureKey} — {@code (year, round, tournament, ymd, tm,
 * home, away)}, with no {@code gameType} so the two markets meet. It includes 회차 deliberately: the same fixture
 * is routinely listed in two consecutive 회차, and the 2-way row of the 회차 actually being picked is
 * the one whose price and settlement belong together.
 */
public final class MarketPairIndex {

    private final Map<FixtureKey, BaseballResult> twoWayByFixture;

    private MarketPairIndex(Map<FixtureKey, BaseballResult> twoWayByFixture) {
        this.twoWayByFixture = twoWayByFixture;
    }

    public static MarketPairIndex empty() {
        return new MarketPairIndex(Map.of());
    }

    /** Input order does not matter; duplicates on one fixture resolve to the lowest id. */
    public static MarketPairIndex build(Collection<BaseballResult> twoWayGames) {
        if (twoWayGames == null || twoWayGames.isEmpty()) {
            return empty();
        }
        Map<FixtureKey, BaseballResult> byFixture = new LinkedHashMap<>();
        for (BaseballResult game : twoWayGames) {
            byFixture.merge(FixtureKey.of(game), game,
                    (kept, candidate) -> kept.id() <= candidate.id() ? kept : candidate);
        }
        return new MarketPairIndex(Map.copyOf(byFixture));
    }

    /**
     * The 2-way row for this game's fixture, or {@code null} when the fixture was never listed in
     * that market. A {@code null} means skip the game — there is no fallback, since the alternative
     * would be settling a bet against a row that does not exist.
     */
    public BaseballResult pairOf(BaseballResult game) {
        return game == null ? null : twoWayByFixture.get(FixtureKey.of(game));
    }
}
