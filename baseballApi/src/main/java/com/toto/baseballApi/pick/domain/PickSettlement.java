package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOdds;

/**
 * Settlement math shared by manual settlement and simulation — kept in one place so payouts never
 * diverge.
 *
 * <p>Pays at {@code totalDiv}, the measured payout of the outcome that happened. That is already the
 * published price of the realized side: every row carrying published odds was cross-checked against
 * {@code totalDiv} and matched (docs/statistical-model-design.md §9), so selection (on
 * {@code publishedOdds()}) and settlement quote the same market.
 *
 * <p>Everything except {@link #settle} needs the <em>pre-game</em> price of the backed side, which is
 * a different question from what the winner paid. It normally comes from the row's own published
 * quote — 3-way or 2-way, whichever market the row is in; a leg may instead carry its own
 * {@link BackedPrice} when the algorithm priced it some other way — see {@link #priceOf}.
 */
public final class PickSettlement {

    private PickSettlement() {
    }

    public static BigDecimal settle(
            List<PickDetail> details, Map<Integer, BaseballResult> actualResultsById, BigDecimal inputMoney) {
        BigDecimal combinedOdds = BigDecimal.ONE;
        for (PickDetail detail : details) {
            BaseballResult actual = actualResultsById.get(detail.resultId());
            if (actual == null || !actual.totalResult().equals(detail.totalResult())) {
                return BigDecimal.ZERO;
            }
            combinedOdds = combinedOdds.multiply(BigDecimal.valueOf(actual.totalDiv()));
        }
        // "소수 둘째에서 올림" — round the combined odds UP at the 2nd decimal place.
        combinedOdds = combinedOdds.setScale(2, RoundingMode.CEILING);
        return combinedOdds.multiply(inputMoney).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * The combined pre-game odds of the backed sides, rounded exactly like {@link #settle} rounds the
     * realized combined odds (2 dp, ceiling). This is what a stake-sizing rule must quote: published
     * rows were cross-checked against {@code totalDiv} and matched, so sizing off this number and
     * settling at {@code totalDiv} quote the same market — sizing off anything else can produce "hit
     * the slip, missed the target". {@code null} when any leg has no price at all; such a day is
     * skipped rather than priced from anything the row knows after the fact — pricing it from
     * {@code totalDiv} would mean pricing it from the outcome (설계 결정 D2).
     */
    public static BigDecimal combinedBackedOdds(
            List<PickDetail> details, Map<Integer, BaseballResult> gamesById,
            Map<Integer, BackedPrice> pricesById) {
        BigDecimal combined = BigDecimal.ONE;
        for (PickDetail detail : details) {
            BackedPrice price = priceOf(detail, gamesById, pricesById);
            if (price == null) {
                return null;
            }
            combined = combined.multiply(BigDecimal.valueOf(price.odds()));
        }
        return combined.setScale(2, RoundingMode.CEILING);
    }

    /**
     * What this slip was worth <em>under the market's own probabilities</em> — the return a bettor
     * with no information whatsoever expects from it.
     *
     * <p>It has a closed form. De-vigging divides each inverse odd by the overround {@code O}, so
     * for any side {@code s} the market's probability is {@code (1/odds_s)/O} and its expected
     * return is {@code p_s × odds_s = 1/O} — the same number whichever side is backed. A slip of N
     * legs is therefore worth {@code Π(1/O_i)} of its stake, and picking better sides cannot move
     * that figure; only information the odds do not contain can.
     *
     * <p>This is the benchmark the goal is stated against. Raw ROI conflates two questions — "did it
     * earn?" and "did it know something?" — and in a market with a 15% margin the first is nearly
     * always no, which tells you nothing about the second. Subtracting this leaves the part that is
     * actually attributable to the strategy, and it self-normalises across leg counts: more legs
     * lower both the return and the benchmark together.
     *
     * <p>Because {@code O} is the margin of whichever market the leg was quoted in, a 승패 leg is
     * measured against the 2-way margin (≈14.1%) and a 승1패 leg against the 3-way one (≈15.2%) —
     * which is the point: switching to a cheaper market is a real gain and has to show up as one.
     *
     * <p>{@code null} when any leg has no price, since a benchmark cannot be quoted for a price that
     * was never observed.
     */
    public static BigDecimal marketExpectation(
            List<PickDetail> details, Map<Integer, BaseballResult> gamesById,
            Map<Integer, BackedPrice> pricesById, BigDecimal inputMoney) {
        BigDecimal factor = BigDecimal.ONE;
        for (PickDetail detail : details) {
            BackedPrice price = priceOf(detail, gamesById, pricesById);
            if (price == null) {
                return null;
            }
            factor = factor.divide(
                    BigDecimal.valueOf(1.0 + price.overround()), 12, RoundingMode.HALF_UP);
        }
        return factor.multiply(inputMoney).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * The same slip taken apart: each leg settled as a single bet of one unit, against the same
     * market benchmark. See {@link LegTally} for why the goal is measured here rather than on the
     * slip. Legs without a price are skipped, since neither side of the comparison exists for them.
     */
    public static LegTally legTally(
            List<PickDetail> details, Map<Integer, BaseballResult> gamesById,
            Map<Integer, BackedPrice> pricesById) {
        int count = 0;
        int hitCount = 0;
        BigDecimal payoutTotal = BigDecimal.ZERO;
        BigDecimal benchmarkTotal = BigDecimal.ZERO;
        BigDecimal payoutSquareTotal = BigDecimal.ZERO;

        for (PickDetail detail : details) {
            BaseballResult game = gamesById.get(detail.resultId());
            BackedPrice price = priceOf(detail, gamesById, pricesById);
            if (price == null) {
                continue;
            }
            count++;
            benchmarkTotal = benchmarkTotal.add(
                    BigDecimal.ONE.divide(
                            BigDecimal.valueOf(1.0 + price.overround()), 12, RoundingMode.HALF_UP));
            if (game.totalResult().equals(detail.totalResult())) {
                hitCount++;
                double payout = price.odds();
                payoutTotal = payoutTotal.add(BigDecimal.valueOf(payout));
                payoutSquareTotal = payoutSquareTotal.add(BigDecimal.valueOf(payout * payout));
            }
            // A losing leg contributes 0 to both sums, which is already correct.
        }
        return new LegTally(count, hitCount, payoutTotal, benchmarkTotal, payoutSquareTotal);
    }

    /**
     * The pre-game price of this leg's backed side: whatever the algorithm attached, else the row's
     * own published quote. {@code null} means "no price" — the caller skips the leg or the day.
     *
     * <p>Which quote a row has depends on its market: a 3-way row (승1패) fills all three
     * {@code PUB_*} columns, a 2-way one (승패, 핸디캡, 언더오버, SUM) fills two and leaves the middle
     * null. Both are read here, so a leg is priced correctly whichever market it sits in and an
     * algorithm only needs to attach a {@link BackedPrice} when it prices legs some other way.
     *
     * <p>What is never a fallback is the row's own {@code totalDiv}: only the realized side is
     * measured there, so using it as a pre-game price would partly encode the outcome — 설계 결정 D2
     * exactly. A missing price is a skipped leg, not a guessed one.
     */
    private static BackedPrice priceOf(
            PickDetail detail, Map<Integer, BaseballResult> gamesById,
            Map<Integer, BackedPrice> pricesById) {
        BaseballResult game = gamesById.get(detail.resultId());
        if (game == null) {
            return null;
        }
        BackedPrice supplied = pricesById == null ? null : pricesById.get(detail.resultId());
        if (supplied != null) {
            return supplied;
        }
        ThreeWayOdds threeWay = game.publishedOdds();
        if (threeWay != null) {
            return new BackedPrice(
                    backedOdds(threeWay, detail.totalResult()), threeWay.overround());
        }
        TwoWayOdds twoWay = game.publishedTwoWayOdds();
        return twoWay == null
                ? null
                : new BackedPrice(backedOdds(twoWay, detail.totalResult()), twoWay.overround());
    }

    private static double backedOdds(ThreeWayOdds odds, String predicted) {
        return switch (predicted) {
            case "승" -> odds.home();
            case "1" -> odds.draw();
            case "패" -> odds.away();
            default -> throw new IllegalArgumentException("Unknown predicted result: " + predicted);
        };
    }

    /**
     * The 2-way markets share one price pair but not one vocabulary: 승패/핸디캡 settle 승/패,
     * 언더오버 settles 언더/오버, SUM settles 홀/짝. All three name the home-side price first, which is
     * the order the columns are in.
     */
    private static double backedOdds(TwoWayOdds odds, String predicted) {
        return switch (predicted) {
            case "승", "언더", "홀" -> odds.home();
            case "패", "오버", "짝" -> odds.away();
            default -> throw new IllegalArgumentException("Unknown predicted result: " + predicted);
        };
    }
}
