package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import com.toto.baseballApi.baseballresult.domain.FixtureKey;

/**
 * A pick recorded <em>before</em> its games were played, and settled afterwards.
 *
 * <p>Every number this project has produced so far was measured on days that were already available
 * while the parameters were being chosen, which is why four separate "goal achieved" verdicts turned
 * out to be window effects (docs/statistical-model-design.md §10, §15). A forward pick is the one
 * measurement that cannot be contaminated that way: the games did not exist when the parameters were
 * frozen, so no amount of searching could have fitted them.
 *
 * <p>That property is only real if the record is immutable. {@link #createdAt} is the evidence the
 * pick predates the game, and {@link #inputMoney} / {@link #combinedOdds} / each leg's
 * {@code backedOdds} are the prices actually seen at bet time — recomputing them later would erase
 * what the decision was based on, and for a martingale it would erase the basis of the stake itself.
 * Only settlement fields are written after the fact.
 *
 * @param paramSignature the frozen parameters, so the record says exactly what is being tested
 * @param bucket         the 조합버킷 this slot belongs to ({@code PickUniverse.combinationBucket})
 * @param combinedOdds   product of the backed sides' pre-game prices; {@code null} when unpriced
 */
public record ForwardPick(
        Integer id,
        String algorithmCode,
        String paramSignature,
        String userName,
        String ymd,
        String bucket,
        int slipNo,
        BigDecimal inputMoney,
        BigDecimal combinedOdds,
        ForwardPickStatus status,
        BigDecimal outputMoney,
        LocalDateTime createdAt,
        LocalDateTime settledAt,
        List<ForwardPickLeg> legs) {

    /** A settled pick that paid. Unsettled picks are not hits — they are not yet anything. */
    public boolean hit() {
        return status == ForwardPickStatus.SETTLED
                && outputMoney != null && outputMoney.signum() > 0;
    }

    public boolean settled() {
        return status == ForwardPickStatus.SETTLED;
    }

    /**
     * This slip taken leg by leg, priced at the odds recorded before the game.
     *
     * <p>Computed from the record itself rather than by re-reading {@code baseball_result}, because
     * the record <em>is</em> the evidence — {@code backedOdds} and {@code overround} are what was
     * actually seen and staked on, and recomputing them from today's data would quietly replace the
     * measurement with a reconstruction. The arithmetic is deliberately the same as
     * {@code PickSettlement.legTally} so a forward number and a backtest number mean the same thing.
     *
     * <p>{@link LegTally#EMPTY} until the pick is settled: an unresolved leg has neither won nor lost,
     * and counting it as a loss would make every pending day look like a bad one.
     */
    public LegTally legTally() {
        if (!settled()) {
            return LegTally.EMPTY;
        }
        int count = 0;
        int hitCount = 0;
        BigDecimal payoutTotal = BigDecimal.ZERO;
        BigDecimal benchmarkTotal = BigDecimal.ZERO;
        BigDecimal payoutSquareTotal = BigDecimal.ZERO;
        for (ForwardPickLeg leg : legs) {
            if (leg.legResult() == null) {
                continue;
            }
            count++;
            benchmarkTotal = benchmarkTotal.add(leg.benchmarkValue());
            if (leg.legHit()) {
                hitCount++;
                BigDecimal payout = leg.backedOdds();
                payoutTotal = payoutTotal.add(payout);
                payoutSquareTotal = payoutSquareTotal.add(payout.multiply(payout));
            }
        }
        return new LegTally(count, hitCount, payoutTotal, benchmarkTotal, payoutSquareTotal);
    }

    /**
     * What this slip was worth under the market's own probabilities — {@code Π(1/(1+overround))} of
     * the stake, the closed form {@code PickSettlement.marketExpectation} uses. {@code null} when the
     * slip has no legs, since a benchmark cannot be quoted for nothing.
     */
    public BigDecimal benchmarkOutputMoney() {
        if (legs.isEmpty()) {
            return null;
        }
        BigDecimal factor = BigDecimal.ONE;
        for (ForwardPickLeg leg : legs) {
            factor = factor.divide(
                    BigDecimal.ONE.add(leg.overround()), 12, RoundingMode.HALF_UP);
        }
        return factor.multiply(inputMoney).setScale(0, RoundingMode.HALF_UP);
    }

    /** One leg: which fixture, in which market, backed at which side and price. */
    public record ForwardPickLeg(
            int legNo,
            FixtureKey fixture,
            String gameType,
            String totalResult,
            BigDecimal backedOdds,
            BigDecimal overround,
            Integer resultId,
            String legResult) {

        /** The price as {@link BackedPrice}, for the settlement helpers. */
        public BackedPrice price() {
            return new BackedPrice(backedOdds.doubleValue(), overround.doubleValue());
        }

        /** Whether the backed side is what happened. False while unsettled. */
        public boolean legHit() {
            return legResult != null && legResult.equals(totalResult);
        }

        /**
         * {@code 1/(1+overround)} — what one unit on this leg was worth under the market's own
         * probabilities, whichever side was backed (the de-vig identity). Uses the margin recorded at
         * bet time, which matters because the 2-way overround drifts: assuming a fixed one would
         * mismeasure the benchmark by about as much as the edge being hunted.
         */
        public BigDecimal benchmarkValue() {
            return BigDecimal.ONE.divide(
                    BigDecimal.ONE.add(overround), 12, RoundingMode.HALF_UP);
        }
    }
}
