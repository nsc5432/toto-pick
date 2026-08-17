package com.toto.baseballApi.baseballresult.domain;

/**
 * What makes two rows the same game.
 *
 * <p>Deliberately excludes {@code gameType}: the 승1패 and 승패 listings of one fixture are the same
 * game in two markets, and pairing them is the point (see {@code MarketPairIndex}). It does include
 * 회차, because the same fixture is routinely listed in two consecutive rounds at different prices and
 * a pick taken from one round must settle against that round's own row.
 *
 * <p>This is the join a forward pick needs. Before a game is played there is no
 * {@code baseball_result} row to point at, so a pre-game pick records the fixture itself and resolves
 * the row later, when it exists.
 */
public record FixtureKey(
        Integer year,
        Integer round,
        String tournament,
        String ymd,
        String tm,
        String home,
        String away) {

    public static FixtureKey of(BaseballResult game) {
        return new FixtureKey(
                game.year(), game.round(), game.tournament(),
                game.ymd(), game.tm(), game.home(), game.away());
    }
}
