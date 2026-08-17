package com.toto.baseballApi.pick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.toto.baseballApi.baseballresult.domain.FixtureKey;

class ForwardPickTest {

    private static final BigDecimal OVERROUND = new BigDecimal("0.140000");

    private ForwardPick.ForwardPickLeg leg(
            int legNo, String predicted, String odds, String legResult) {
        return new ForwardPick.ForwardPickLeg(
                legNo,
                new FixtureKey(2026, 97, "KBO", "260818", "19:00", "H" + legNo, "A" + legNo),
                "야구 승패", predicted, new BigDecimal(odds), OVERROUND,
                legResult == null ? null : 1000 + legNo, legResult);
    }

    private ForwardPick pick(
            ForwardPickStatus status, String outputMoney, ForwardPick.ForwardPickLeg... legs) {
        return new ForwardPick(
                1, "WIN_RATE_ODDS_2WAY", "x=1.6", "NSC", "260818", "KBO_NPB", 0,
                BigDecimal.valueOf(1000), new BigDecimal("2.74"), status,
                outputMoney == null ? null : new BigDecimal(outputMoney),
                LocalDateTime.of(2026, 8, 17, 19, 57), null, List.of(legs));
    }

    @Test
    void tallisEachLegAtThePriceRecordedBeforeTheGame() {
        ForwardPick settled = pick(ForwardPickStatus.SETTLED, "0",
                leg(0, "패", "1.65", "패"),   // hit
                leg(1, "패", "1.66", "승"));  // miss

        LegTally legs = settled.legTally();

        assertThat(legs.count()).isEqualTo(2);
        assertThat(legs.hitCount()).isEqualTo(1);
        // Only the winning leg pays, and it pays the odds as recorded — not a price re-derived today.
        assertThat(legs.returnRate()).isEqualByComparingTo("-0.1750");
        // 1/1.14 = 0.8772 per leg, from each leg's own stored margin.
        assertThat(legs.benchmarkRate()).isEqualByComparingTo("-0.1228");
        assertThat(legs.excessReturn()).isEqualByComparingTo("-0.0522");
        // The squared term is carried, so a significance test is possible once days are merged.
        assertThat(legs.standardError()).isNotNull();
    }

    @Test
    void countsNothingWhileTheGamesHaveNotResolved() {
        ForwardPick pending = pick(ForwardPickStatus.PENDING, null,
                leg(0, "패", "1.65", null),
                leg(1, "패", "1.66", null));

        // An unresolved bet has not won or lost. Treating it as a loss would make every pending day
        // look like a bad one and drag the running total down for nothing.
        assertThat(pending.legTally()).isEqualTo(LegTally.EMPTY);
        assertThat(pending.settled()).isFalse();
        assertThat(pending.hit()).isFalse();
    }

    @Test
    void benchmarksTheSlipAtTheProductOfItsLegsMargins() {
        ForwardPick settled = pick(ForwardPickStatus.SETTLED, "0",
                leg(0, "패", "1.65", "패"),
                leg(1, "패", "1.66", "승"));

        // (1/1.14)^2 × 1,000 = 769 — the closed form PickSettlement.marketExpectation uses, so a
        // forward benchmark and a backtest benchmark are the same quantity.
        assertThat(settled.benchmarkOutputMoney()).isEqualByComparingTo("769");
    }

    @Test
    void aSettledPickWithNoPayoutIsAMissRatherThanUnresolved() {
        assertThat(pick(ForwardPickStatus.SETTLED, "0", leg(0, "패", "1.65", "승")).hit()).isFalse();
        assertThat(pick(ForwardPickStatus.SETTLED, "2320", leg(0, "패", "1.65", "패")).hit()).isTrue();
    }

    @Test
    void legHitComparesTheRecordedPredictionWithWhatHappened() {
        assertThat(leg(0, "패", "1.65", "패").legHit()).isTrue();
        assertThat(leg(0, "패", "1.65", "승").legHit()).isFalse();
        assertThat(leg(0, "패", "1.65", null).legHit()).isFalse();
    }
}
