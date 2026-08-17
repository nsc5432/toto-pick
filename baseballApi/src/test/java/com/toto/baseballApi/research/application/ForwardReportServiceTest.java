package com.toto.baseballApi.research.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.FixtureKey;
import com.toto.baseballApi.pick.domain.ForwardPick;
import com.toto.baseballApi.pick.domain.ForwardPickRepository;
import com.toto.baseballApi.pick.domain.ForwardPickStatus;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickSlip;
import com.toto.baseballApi.pick.domain.SlipSelectionInput;
import com.toto.baseballApi.research.domain.ForwardReport;

class ForwardReportServiceTest {

    private static final String CODE = "WIN_RATE_ODDS_2WAY";
    private static final BigDecimal OVERROUND = new BigDecimal("0.140000");
    /** 1/1.14 = 0.8772 per leg — the benchmark every leg here is measured against. */
    private static final String LEG_BENCHMARK_RATE = "-0.1228";

    private final List<ForwardPick> picks = new ArrayList<>();
    private final List<BaseballResult> results = new ArrayList<>();

    private ForwardReportService service() {
        return new ForwardReportService(
                new FakeForwardPickRepository(picks),
                new FakeBaseballResultRepository(results),
                new ResearchProperties(null, null, null),
                List.of(new StubAlgorithm()));
    }

    /**
     * One settled 2-leg slip plus its two 승패 result rows.
     *
     * @param homeResult what actually happened, as the 승패 market settles it
     */
    private void recordSlip(
            int id, String ymd, String predicted, String odds, String homeResult, String output) {
        int resultId = id * 10;
        picks.add(new ForwardPick(
                id, CODE, "x=1.6", "NSC", ymd, "KBO_NPB", 0,
                BigDecimal.valueOf(1000), new BigDecimal("2.74"),
                ForwardPickStatus.SETTLED, new BigDecimal(output),
                LocalDateTime.of(2026, 8, 17, 19, 0), LocalDateTime.of(2026, 8, 19, 9, 0),
                List.of(new ForwardPick.ForwardPickLeg(
                        0, new FixtureKey(2026, 97, "KBO", ymd, "19:00", "HOME", "AWAY"),
                        "야구 승패", predicted, new BigDecimal(odds), OVERROUND,
                        resultId, homeResult))));
        // PUB_DRAW_DIV null — a 2-way row, priced 1.75 home / 2.24 away.
        results.add(new BaseballResult(resultId, 2026, 97, "KBO", ymd, "19:00", "HOME", "AWAY",
                "야구 승패", null, 0.0, null, homeResult,
                "패".equals(homeResult) ? 2.24 : 1.75,
                1.75, null, 2.24));
    }

    @Test
    void countsOnlySettledSlipsButReportsThePendingOnes() {
        recordSlip(1, "260818", "패", "2.24", "패", "2240");
        picks.add(new ForwardPick(
                2, CODE, "x=1.6", "NSC", "260819", "MLB", 0,
                BigDecimal.valueOf(1000), new BigDecimal("2.74"),
                ForwardPickStatus.PENDING, null,
                LocalDateTime.of(2026, 8, 18, 19, 0), null,
                List.of(new ForwardPick.ForwardPickLeg(
                        0, new FixtureKey(2026, 97, "MLB", "260819", "08:00", "H", "A"),
                        "야구 승패", "패", new BigDecimal("1.90"), OVERROUND, null, null))));

        ForwardReport report = service().reports().get(0);

        assertThat(report.slipCount()).isEqualTo(2);
        assertThat(report.settledSlipCount()).isEqualTo(1);
        assertThat(report.pendingSlipCount()).isEqualTo(1);
        assertThat(report.pendingYmds()).containsExactly("260819");
        // The pending slip contributes no legs at all — not a leg counted as a loss.
        assertThat(report.metrics().legs().count()).isEqualTo(1);
        assertThat(report.metrics().slipCount()).isEqualTo(1);
    }

    @Test
    void measuresLegExcessAgainstEachLegsOwnRecordedMargin() {
        recordSlip(1, "260818", "패", "2.24", "패", "2240");   // hit
        recordSlip(2, "260819", "패", "2.24", "승", "0");      // miss

        ForwardReport report = service().reports().get(0);

        assertThat(report.metrics().legs().count()).isEqualTo(2);
        assertThat(report.metrics().legs().hitCount()).isEqualTo(1);
        assertThat(report.metrics().legs().benchmarkRate()).isEqualByComparingTo(LEG_BENCHMARK_RATE);
        // (2.24 + 0)/2 − 1 = +0.12 realized, against −0.1228 no-skill.
        assertThat(report.metrics().legs().returnRate()).isEqualByComparingTo("0.1200");
        assertThat(report.metrics().legExcessReturn()).isEqualByComparingTo("0.2428");
    }

    @Test
    void judgesTheForwardTestByTheSameDeclaredGoalASweepIs() {
        recordSlip(1, "260818", "패", "2.24", "패", "2240");

        ForwardReport report = service().reports().get(0);

        // One slip cannot clear a 100-slip gate, and that is the point: the sample gates are the first
        // thing a forward test has to outgrow, and no forward-only bar exists to shortcut them.
        assertThat(report.verdict().achieved()).isFalse();
        assertThat(report.verdict().checks())
                .anySatisfy(check -> assertThat(check.name()).isEqualTo("최소 조합 수"));
    }

    @Test
    void exposesAOneSidedCandidateThroughTheBackedSideCounts() {
        recordSlip(1, "260818", "패", "2.24", "패", "2240");
        recordSlip(2, "260819", "패", "2.24", "승", "0");

        // Every leg on one side is the tell that a "form × odds" rule has become "fade the home team".
        assertThat(service().reports().get(0).backedSideCounts()).containsExactly(
                org.assertj.core.api.Assertions.entry("패", 2));
    }

    @Test
    void comparesThePicksWithBackingAwayOnTheSameLegs() {
        // Leg 1 backed away and away won; leg 2 backed away and home won. The control is identical
        // here by construction, which is exactly what it should report for an all-패 candidate.
        recordSlip(1, "260818", "패", "2.24", "패", "2240");
        recordSlip(2, "260819", "패", "2.24", "승", "0");

        ForwardReport report = service().reports().get(0);

        assertThat(report.awayBaseline().count()).isEqualTo(2);
        assertThat(report.awayBaseline().hitCount()).isEqualTo(1);
        assertThat(report.excessOverAwayBaseline()).isEqualByComparingTo("0.0000");
    }

    @Test
    void separatesThePicksFromTheAwayBaselineWhenTheyDiffer() {
        // Backed home at 1.75 and home won. Backing away instead would have lost, so the picks must
        // score above the control — the control is only a tie when the picks *are* the control.
        recordSlip(1, "260818", "승", "1.75", "승", "1750");

        ForwardReport report = service().reports().get(0);

        assertThat(report.metrics().legs().hitCount()).isEqualTo(1);
        assertThat(report.awayBaseline().hitCount()).isZero();
        assertThat(report.excessOverAwayBaseline()).isPositive();
    }

    @Test
    void reportsADayThatWasPlayedButNeverPicked() {
        recordSlip(1, "260818", "패", "2.24", "패", "2240");
        // 260819 finished with games on record, and no pick was ever made for it. Append-only means
        // that day can never be recovered, so it has to be visible rather than silently absent.
        results.add(new BaseballResult(999, 2026, 98, "KBO", "260819", "19:00", "H", "A",
                "야구 승패", null, 0.0, null, "승", 1.80, 1.75, null, 2.24));

        ForwardReport.Coverage coverage = service().reports().get(0).coverage();

        assertThat(coverage.firstYmd()).isEqualTo("260818");
        assertThat(coverage.playedDays()).isEqualTo(2);
        assertThat(coverage.coveredDays()).isEqualTo(1);
        assertThat(coverage.missedYmds()).containsExactly("260819");
    }

    @Test
    void hasNothingToSayBeforeTheFirstPick() {
        assertThat(service().reports()).isEmpty();
    }

    private record FakeForwardPickRepository(List<ForwardPick> picks) implements ForwardPickRepository {

        @Override
        public ForwardPick append(ForwardPick pick) {
            throw new UnsupportedOperationException("read-only fake");
        }

        @Override
        public boolean existsSlip(String code, String userName, String ymd, String bucket, int slipNo) {
            return false;
        }

        @Override
        public List<ForwardPick> findByAlgorithm(String algorithmCode, String userName) {
            return picks.stream()
                    .filter(pick -> pick.algorithmCode().equals(algorithmCode))
                    .filter(pick -> pick.userName().equals(userName))
                    .toList();
        }

        @Override
        public List<ForwardPick> findAll() {
            return List.copyOf(picks);
        }

        @Override
        public void settle(ForwardPick settled) {
            throw new UnsupportedOperationException("read-only fake");
        }
    }

    private record FakeBaseballResultRepository(List<BaseballResult> rows)
            implements BaseballResultRepository {

        @Override
        public Page<BaseballResult> findAll(Pageable pageable) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<BaseballResult> findByYearAndRound(Integer year, Integer round) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<BaseballResult> findAllById(Collection<Integer> ids) {
            return rows.stream().filter(row -> ids.contains(row.id())).toList();
        }

        @Override
        public List<BaseballResult> findByGameType(String gameType) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<BaseballResult> findByGameTypeAndTournamentsAndYmdBetween(
                String gameType, Collection<String> tournaments, String from, String to) {
            return rows.stream()
                    .filter(row -> row.gameType().equals(gameType))
                    .filter(row -> tournaments.contains(row.tournament()))
                    .filter(row -> row.ymd().compareTo(from) >= 0 && row.ymd().compareTo(to) <= 0)
                    .toList();
        }

        @Override
        public List<BaseballResult> findByGameTypeAndTournamentsAndYmdBefore(
                String gameType, Collection<String> tournaments, String ymdExclusive) {
            throw new UnsupportedOperationException("not needed");
        }
    }

    private static final class StubAlgorithm implements PickAlgorithm {

        @Override
        public String code() {
            return CODE;
        }

        @Override
        public String name() {
            return "승률-배당 괴리(승패)";
        }

        @Override
        public List<PickSlip> selectSlips(SlipSelectionInput input) {
            return List.of();
        }
    }
}
