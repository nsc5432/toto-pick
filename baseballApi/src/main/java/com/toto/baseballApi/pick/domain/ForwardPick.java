package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
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
    }
}
