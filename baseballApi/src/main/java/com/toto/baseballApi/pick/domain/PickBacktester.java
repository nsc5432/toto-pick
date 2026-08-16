package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
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
            List<PickDetail> details = slip.selections().stream()
                    .map(s -> new PickDetail(null, s.resultId(), s.predictedTotalResult()))
                    .toList();
            settled.add(new SettledSlip(
                    details, inputMoney, PickSettlement.settle(details, dayGamesById, inputMoney)));
        }
        return settled;
    }
}
