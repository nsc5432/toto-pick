package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * Runs one algorithm over one day and settles the slips it produced — the single place where
 * "select, then settle" lives.
 *
 * <p>Both consumers go through here so their numbers can never drift apart: the persisting
 * simulation ({@code PickSimulationService}) saves each {@link SettledSlip} as a {@code pick_mstr}
 * row, while the research backtest only aggregates them in memory. A parameter sweep evaluates
 * thousands of candidates, so it must not write to the database — but it must settle them by
 * exactly the rules the real simulation uses, or the search would optimize a fiction.
 */
public final class PickBacktester {

    private PickBacktester() {
    }

    public static List<SettledSlip> runDay(
            PickAlgorithm algorithm, SlipSelectionInput input,
            Map<Integer, BaseballResult> dayGamesById, BigDecimal inputMoney) {

        List<PickSlip> slips = algorithm.selectSlips(input);
        List<SettledSlip> settled = new ArrayList<>(slips.size());
        for (PickSlip slip : slips) {
            settled.add(settle(slip, dayGamesById, inputMoney));
        }
        return settled;
    }

    /**
     * The staked counterpart of {@link #runDay}: the stake comes from the algorithm's own
     * {@link MartingaleStaking} session instead of a flat {@code inputMoney}, and the session is
     * told the outcome so the next slot's stake sees it.
     *
     * <p>A slot is one {@code (ymd, PickUniverse#combinationBucket)} pair — the games that may share
     * a slip and that all resolve before the next slot starts. Only the algorithm's first slip is
     * considered: a staking algorithm bets one slip per slot by design (R2), since same-slot games
     * run concurrently. An empty return means "no bet" (no slip or no usable price) and leaves the
     * session untouched. Slots must be fed in chronological order — the fold is what makes the
     * martingale path real, so the caller must not parallelize or reorder slots within one session.
     */
    public static List<SettledSlip> runStakedSlot(
            StakingAlgorithm algorithm, SlipSelectionInput input,
            Map<Integer, BaseballResult> slotGamesById, MartingaleStaking staking) {

        List<PickSlip> slips = algorithm.selectSlips(input);
        if (slips.isEmpty()) {
            return List.of();
        }
        PickSlip slip = slips.get(0);
        List<PickDetail> details = toDetails(slip);
        Map<Integer, BackedPrice> prices = pricesOf(slip);
        BigDecimal combinedOdds =
                PickSettlement.combinedBackedOdds(details, slotGamesById, prices);
        BigDecimal stake = staking.nextStake(combinedOdds);
        if (stake == null) {
            return List.of();
        }
        SettledSlip settled = settle(slip, slotGamesById, stake);
        staking.settle(stake, settled.hit());
        return List.of(settled);
    }

    private static List<PickDetail> toDetails(PickSlip slip) {
        return slip.selections().stream()
                .map(s -> new PickDetail(null, s.resultId(), s.predictedTotalResult()))
                .toList();
    }

    /**
     * The prices the algorithm attached to its own legs, if any. Empty for every algorithm betting
     * the 3-way market, which lets settlement read the price off the game itself.
     */
    private static Map<Integer, BackedPrice> pricesOf(PickSlip slip) {
        Map<Integer, BackedPrice> prices = new LinkedHashMap<>();
        for (PickSelection selection : slip.selections()) {
            if (selection.price() != null) {
                prices.put(selection.resultId(), selection.price());
            }
        }
        return prices;
    }

    private static SettledSlip settle(
            PickSlip slip, Map<Integer, BaseballResult> dayGamesById, BigDecimal stake) {
        List<PickDetail> details = toDetails(slip);
        Map<Integer, BackedPrice> prices = pricesOf(slip);
        return new SettledSlip(
                details, stake,
                PickSettlement.settle(details, dayGamesById, stake),
                PickSettlement.marketExpectation(details, dayGamesById, prices, stake),
                PickSettlement.legTally(details, dayGamesById, prices));
    }
}
