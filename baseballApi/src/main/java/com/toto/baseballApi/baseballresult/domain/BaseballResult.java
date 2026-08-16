package com.toto.baseballApi.baseballresult.domain;

/**
 * One result row. Two sets of 3-way odds live here and they are not interchangeable.
 *
 * @param totalDiv    the measured payout of the outcome that actually happened — the only odds this
 *                    row ever held natively, and what settlement pays out at
 * @param homeDiv     backfilled <em>estimate</em> (see {@code BaseballResultDivBackfillService}),
 *                    derived from {@code totalDiv} plus an assumed overround. Never read these three
 *                    as a selection signal: the realized side carries the true price while the other
 *                    two are reconstructed, so any rule reading them learns the outcome
 *                    (docs/statistical-model-design.md, 설계 결정 D2)
 * @param drawDiv     see {@code homeDiv}
 * @param awayDiv     see {@code homeDiv}
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
        Double homeDiv,
        Double drawDiv,
        Double awayDiv,
        Double pubHomeDiv,
        Double pubDrawDiv,
        Double pubAwayDiv) {

    /**
     * The published 3-way price, or {@code null} when this game has none.
     *
     * <p>This is the single entry point an algorithm should use to read odds. Going through it makes
     * "select on published odds only" structural rather than a rule every author has to remember —
     * the same reason team form is read through {@code TeamFormIndex} instead of by walking history.
     * A {@code null} means skip the game; there is deliberately no fallback to the backfilled
     * columns, because falling back would silently reintroduce exactly the bias D2 identified.
     */
    public ThreeWayOdds publishedOdds() {
        return ThreeWayOdds.of(pubHomeDiv, pubDrawDiv, pubAwayDiv);
    }
}
