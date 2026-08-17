package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link FavoriteTwoWaySlipAlgorithm} selection under martingale staking — the 승패-market sibling of
 * {@link FavoriteMartingaleAlgorithm}, and the reason the 2-way plumbing exists.
 *
 * <p>Structurally identical to its 3-way sibling: selection is delegated with {@code combinedN}
 * pinned to 2 and only the first slip kept (R2 — one slip per slot, since same-slot games run
 * concurrently), the stake is computed by the evaluator's {@link MartingaleStaking} session, and the
 * 50,000원 sequence budget is unchanged.
 *
 * <p><strong>What changing the market does to the sequence.</strong> Two effects pull against each
 * other. Hits get roughly twice as common — a 2-leg slip converts near 0.35 instead of 0.20, because
 * a one-run win is no longer a loss. But the combined odds fall with it (about 2.3 rather than 3.0),
 * and the martingale's step multiplier is {@code O/(O−1)}, so each miss escalates the stake faster:
 * the 50,000원 budget covers about 7 steps here against 10 there. Measured, the two effects roughly
 * cancel on drawdown and the observed maxDrawdown came out <em>worse</em>, not better
 * (docs/martingale-staking-design.md §10) — the prediction that it would fall was wrong, and it is
 * recorded here so the next reader does not re-derive it.
 *
 * <p><strong>What it does not do.</strong> It does not make the strategy profitable. The 2-way margin
 * (14.1%) is lower than the 3-way one (15.2%), so the bleed per leg improves from −13.2% to −12.4%,
 * but it stays negative and no staking rule can change that sign
 * (docs/martingale-staking-design.md §8). Judge this the same way as everything else: on excess
 * return against the market benchmark, with hit rate and budget-exhaustion rate read as diagnostics.
 *
 * <p>Stakes are sized off the 승패 row's own published price. They used to be sized off an estimate of
 * it, which mattered here more than anywhere else: the stake is a function of the price, so a biased
 * price shows up as "hit the slip, missed the target" rather than as an obvious failure.
 */
public class FavoriteTwoWayMartingaleAlgorithm implements StakingAlgorithm, TunableAlgorithm {

    public static final String CODE = "FAVORITE_2WAY_MARTINGALE";

    /** 적중 시 (누적손실 회수 후) 확보할 목표수익금, 원. */
    public static final String TARGET_PROFIT = "targetProfit";

    private static final int MARTINGALE_COMBINED_N = 2;
    private static final double DEFAULT_TARGET_PROFIT = 1000;

    private final FavoriteTwoWaySlipAlgorithm delegate = new FavoriteTwoWaySlipAlgorithm();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "배당 우세팀 마틴게일(승패)";
    }

    @Override
    public ParamSpace paramSpace() {
        return ParamSpace.of(
                new ParamSpec(TARGET_PROFIT, 500, 5000, 500, DEFAULT_TARGET_PROFIT),
                // Pinned, not swept: "2게임 조합" is the premise of this strategy, and declaring it
                // keeps the persisted simulation (which runs with empty params) at the same value.
                ParamSpec.fixed(AlgorithmParams.COMBINED_N, MARTINGALE_COMBINED_N));
    }

    @Override
    public List<PickSlip> selectSlips(SlipSelectionInput input) {
        SlipSelectionInput forced = new SlipSelectionInput(
                input.ymd(), input.num(), input.x(), input.y(), MARTINGALE_COMBINED_N,
                input.dayGames(), input.historyGames(), input.formIndex(), input.marketPairs(),
                input.params());
        List<PickSlip> slips = delegate.selectSlips(forced);
        return slips.isEmpty() ? List.of() : List.of(slips.get(0));
    }

    @Override
    public MartingaleStaking newStakingSession(AlgorithmParams params) {
        return new MartingaleStaking(
                BigDecimal.valueOf(params.get(TARGET_PROFIT, DEFAULT_TARGET_PROFIT)));
    }
}
