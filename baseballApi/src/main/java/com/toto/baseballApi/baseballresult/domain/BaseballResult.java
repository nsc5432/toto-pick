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
 * <p><strong>How many published prices a row has depends on its market.</strong> The 3-way markets
 * (승1패, 전반 승무패) fill all three {@code PUB_*} columns; the 2-way ones (승패, 핸디캡, 언더오버, SUM
 * and their 전반 siblings) fill home and away and leave {@code pubDrawDiv} null, because there is no
 * middle slot to price. Read them through {@link #publishedOdds()} / {@link #publishedTwoWayOdds()},
 * which return {@code null} for the market they do not describe.
 *
 * @param totalDiv    the measured payout of the outcome that actually happened — the only odds this
 *                    row ever held natively, and what settlement pays out at. Known only after the
 *                    game, so it is never a selection signal either
 * @param pubHomeDiv  the odds as published before the game (wisetoto 프로토 승부식). Genuine market
 *                    prices — the only odds an algorithm may select on
 * @param pubDrawDiv  see {@code pubHomeDiv}; null on the 2-way markets
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

    /**
     * The published 2-way price, or {@code null} when this game has none.
     *
     * <p>The 2-way sibling of {@link #publishedOdds()} and subject to every word of it: this is how a
     * 승패/핸디캡/언더오버 algorithm reads its price, and a {@code null} means skip the game rather than
     * fall back to anything. In particular it must never fall back to {@code totalDiv}, which on a
     * 2-way row is the winning side's payout and therefore the outcome (설계 결정 D2).
     *
     * <p>Deliberately ignores {@code pubDrawDiv} instead of demanding it be null, so a 3-way row
     * answers this too — the outright price of a 승1패 row is not published, so what comes back is the
     * 2점차-이상 price and it is only meaningful on a row whose market really has two slots. Callers
     * pick the row, and they pick it by market.
     */
    public TwoWayOdds publishedTwoWayOdds() {
        return TwoWayOdds.of(pubHomeDiv, pubAwayDiv);
    }
}
