package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;

/**
 * Settlement math shared by manual settlement and simulation — kept in one place so payouts never
 * diverge.
 *
 * <p>Pays at {@code totalDiv}, the measured payout of the outcome that happened. That is already the
 * published price of the realized side: every row carrying published odds was cross-checked against
 * {@code totalDiv} and matched (docs/statistical-model-design.md §9), so selection (on
 * {@code publishedOdds()}) and settlement quote the same market.
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
     * <p>{@code null} when any leg lacks published odds, since a benchmark cannot be quoted for a
     * price that was never observed.
     */
    public static BigDecimal marketExpectation(
            List<PickDetail> details, Map<Integer, BaseballResult> gamesById, BigDecimal inputMoney) {
        BigDecimal factor = BigDecimal.ONE;
        for (PickDetail detail : details) {
            BaseballResult game = gamesById.get(detail.resultId());
            ThreeWayOdds odds = game == null ? null : game.publishedOdds();
            if (odds == null) {
                return null;
            }
            factor = factor.divide(
                    BigDecimal.valueOf(1.0 + odds.overround()), 12, RoundingMode.HALF_UP);
        }
        return factor.multiply(inputMoney).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * The same slip taken apart: each leg settled as a single bet of one unit, against the same
     * market benchmark. See {@link LegTally} for why the goal is measured here rather than on the
     * slip. Legs without published odds are skipped, since neither side of the comparison exists
     * for them.
     */
    public static LegTally legTally(
            List<PickDetail> details, Map<Integer, BaseballResult> gamesById) {
        int count = 0;
        int hitCount = 0;
        BigDecimal payoutTotal = BigDecimal.ZERO;
        BigDecimal benchmarkTotal = BigDecimal.ZERO;
        BigDecimal payoutSquareTotal = BigDecimal.ZERO;

        for (PickDetail detail : details) {
            BaseballResult game = gamesById.get(detail.resultId());
            ThreeWayOdds odds = game == null ? null : game.publishedOdds();
            if (odds == null) {
                continue;
            }
            count++;
            benchmarkTotal = benchmarkTotal.add(
                    BigDecimal.ONE.divide(
                            BigDecimal.valueOf(1.0 + odds.overround()), 12, RoundingMode.HALF_UP));
            if (game.totalResult().equals(detail.totalResult())) {
                hitCount++;
                double payout = backedOdds(odds, detail.totalResult());
                payoutTotal = payoutTotal.add(BigDecimal.valueOf(payout));
                payoutSquareTotal = payoutSquareTotal.add(BigDecimal.valueOf(payout * payout));
            }
            // A losing leg contributes 0 to both sums, which is already correct.
        }
        return new LegTally(count, hitCount, payoutTotal, benchmarkTotal, payoutSquareTotal);
    }

    private static double backedOdds(ThreeWayOdds odds, String predicted) {
        return switch (predicted) {
            case "승" -> odds.home();
            case "1" -> odds.draw();
            case "패" -> odds.away();
            default -> throw new IllegalArgumentException("Unknown predicted result: " + predicted);
        };
    }
}
