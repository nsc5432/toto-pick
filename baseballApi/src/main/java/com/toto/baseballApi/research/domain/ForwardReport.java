package com.toto.baseballApi.research.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.pick.domain.LegTally;

/**
 * What the forward test has measured so far for one frozen candidate — the only number in this
 * project that cannot be a window effect.
 *
 * <p>It is deliberately scored by the same {@link ExperimentGoal} and the same
 * {@link BacktestMetrics} arithmetic the backtest uses. A separate forward-only bar would let the
 * project pass a target it could not pass in-sample, which is how the previous five false positives
 * happened in reverse; and a separate metric would make "the backtest said +7%p, the forward test
 * says +4%p" uninterpretable.
 *
 * @param metrics          cumulative over every <em>settled</em> slip. Pending slips contribute
 *                         nothing — an unresolved bet has not won or lost, and counting it either way
 *                         would bias the running total in whichever direction
 * @param verdict          the declared goal applied to {@code metrics}. Almost always a fail early on
 *                         and that is correct: the sample gates exist so a 5-slip run cannot claim
 *                         anything, and they are the first thing a forward test has to outgrow
 * @param awayBaseline     the same settled legs with the away side backed every time, whatever the
 *                         algorithm actually picked. Every leg of the first frozen candidate came out
 *                         {@code 패}, so "does the form filter add anything to fading the home team?"
 *                         is the first question a reader will ask — this is the control that answers
 *                         it. {@code null} when the settled legs' result rows cannot be loaded
 * @param backedSideCounts how many legs backed each side. The tell for a candidate that has quietly
 *                         become a one-sided bet
 * @param coverage         whether the daily loop is actually running; see {@link Coverage}
 */
public record ForwardReport(
        String algorithmCode,
        String algorithmName,
        String userName,
        List<String> paramSignatures,
        int slipCount,
        int settledSlipCount,
        int pendingSlipCount,
        List<String> pendingYmds,
        BacktestMetrics metrics,
        GoalVerdict verdict,
        LegTally awayBaseline,
        Map<String, Integer> backedSideCounts,
        Coverage coverage) {

    /**
     * How much of the elapsed schedule the forward test actually covered.
     *
     * <p>This is here because the forward test's fatal failure mode is not a bad number, it is a
     * missing day. Records are append-only and a fixture that has already been played can never be
     * picked pre-game again, so a day the loop did not run is lost permanently. At roughly ten legs a
     * day, reaching a sample where {@code t} can move takes weeks, and nothing else in the system
     * would report a gap.
     *
     * <p>It cannot tell "the loop did not run" from "the algorithm stood aside": generation writes
     * nothing on a day where no candidate passes the filter, and the append-only record keeps no
     * zero-slip marker to distinguish the two. So read a non-empty {@code missedYmds} as <em>needs
     * checking</em>, not as <em>lost</em>. A 36-day replay never exercised the stand-aside case (it bet
     * every day), so that branch is untested rather than known-good.
     *
     * @param playedDays  days with finished games from {@code firstYmd} onward — the days that were
     *                    available to bet
     * @param coveredDays how many of those have at least one pick on record
     * @param missedYmds  the difference, listed. Non-empty means the measurement may have holes in it
     */
    public record Coverage(
            String firstYmd,
            String lastYmd,
            int playedDays,
            int coveredDays,
            List<String> missedYmds) {

        public static final Coverage NONE = new Coverage(null, null, 0, 0, List.of());
    }

    /** Leg excess of the picks minus leg excess of backing away on the same legs. */
    public BigDecimal excessOverAwayBaseline() {
        if (awayBaseline == null || awayBaseline.count() == 0) {
            return null;
        }
        BigDecimal picks = metrics.legExcessReturn();
        BigDecimal away = awayBaseline.excessReturn();
        return picks == null || away == null ? null : picks.subtract(away);
    }
}
