package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code FAVORITE} selection under martingale staking (docs/martingale-staking-design.md).
 *
 * <p>Delegates selection to {@link FavoriteOddsSlipAlgorithm} pinned at {@code combinedN=2} and
 * keeps only the first slip — the delegate sorts candidates by odds ascending and chunks, so the
 * first slip is the two lowest-odds legs, i.e. the day's highest-probability combination. One slip
 * per day is a hard rule (R2): same-day games run concurrently, so a second slip could never be
 * sized off the first one's outcome.
 *
 * <p>The stake itself is not computed here — selection must stay day-isolated. The evaluator holds
 * a {@link MartingaleStaking} session (via {@link #newStakingSession}) and folds it over the days.
 *
 * <p>{@code targetProfit} is the swept knob: what one hit must clear after recovering the round's
 * losses. Larger targets scale every stake up proportionally, reaching the 50,000원 round budget in
 * fewer misses — the sweep is exactly the target-vs-bust-probability trade-off.
 */
public class FavoriteMartingaleAlgorithm implements StakingAlgorithm, TunableAlgorithm {

    public static final String CODE = "FAVORITE_MARTINGALE";

    /** 적중 시 (누적손실 회수 후) 확보할 목표수익금, 원. */
    public static final String TARGET_PROFIT = "targetProfit";

    private static final int MARTINGALE_COMBINED_N = 2;
    private static final double DEFAULT_TARGET_PROFIT = 1000;

    private final FavoriteOddsSlipAlgorithm delegate = new FavoriteOddsSlipAlgorithm();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "배당 우세팀 마틴게일";
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
                input.dayGames(), input.historyGames(), input.formIndex(), input.params());
        List<PickSlip> slips = delegate.selectSlips(forced);
        return slips.isEmpty() ? List.of() : List.of(slips.get(0));
    }

    @Override
    public MartingaleStaking newStakingSession(AlgorithmParams params) {
        return new MartingaleStaking(
                BigDecimal.valueOf(params.get(TARGET_PROFIT, DEFAULT_TARGET_PROFIT)));
    }
}
