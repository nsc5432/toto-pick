package com.toto.baseballApi.baseballresult.domain;

/**
 * One result row. Exactly two kinds of odds live here, and only one of them may be selected on.
 *
 * <p>A third kind used to exist: {@code HOME_DIV}/{@code DRAW_DIV}/{@code AWAY_DIV}, reverse-engineered
 * from {@code totalDiv} plus an assumed overround. Because only the realized side was measured, which
 * of those three was exact depended on the outcome — so any rule reading them partly learned the
 * result (docs/statistical-model-design.md, 설계 결정 D2). They were removed once the published prices
 * covered the range, precisely so no future author can reach for them.
 *
 * @param totalDiv    the measured payout of the outcome that actually happened — the only odds this
 *                    row ever held natively, and what settlement pays out at. Known only after the
 *                    game, so it is never a selection signal either
 * @param pubHomeDiv  the odds as published before the game (wisetoto 프로토 승부식). Genuine market
 *                    prices — the only odds an algorithm may select on
 * @param pubDrawDiv  see {@code pubHomeDiv}
 * @param pubAwayDiv  see {@code pubHomeDiv}
 */
public record BaseballResult(
        Integer id,
        Integer year,
        Integer round,
        String tournament,
        String ymd,
        String tm,
        String home,
        String away,
        String gameType,
        String cond,
        Double res1,
        Double res2,
        String totalResult,
        Double totalDiv,
        Double pubHomeDiv,
        Double pubDrawDiv,
        Double pubAwayDiv) {

    /**
     * The published 3-way price, or {@code null} when this game has none.
     *
     * <p>This is the single entry point an algorithm should use to read odds. Going through it makes
     * "select on published odds only" structural rather than a rule every author has to remember —
     * the same reason team form is read through {@code TeamFormIndex} instead of by walking history.
     * A {@code null} means skip the game — there is deliberately no fallback. Pricing an unpriced
     * game from anything this row knows would mean pricing it from {@code totalDiv}, which is the
     * outcome, and that is exactly the bias D2 identified.
     */
    public ThreeWayOdds publishedOdds() {
        return ThreeWayOdds.of(pubHomeDiv, pubDrawDiv, pubAwayDiv);
    }
}
