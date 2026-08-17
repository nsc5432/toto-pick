package com.toto.baseballApi.baseballresult.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.DivBackfillResult;
import com.toto.baseballApi.baseballresult.domain.DivUpdate;
import com.toto.baseballApi.baseballresult.domain.TwoWayOddsEstimator;

import lombok.RequiredArgsConstructor;

/**
 * Backfills HOME_DIV / DRAW_DIV / AWAY_DIV for '야구 승1패' rows.
 *
 * <p>A result row only stores the odds of the outcome that actually happened
 * ({@code TOTAL_DIV}), but the three-way market obeys a fixed overround:
 * 1/승 + 1/무 + 1/패 = 1.15 (verified against 13 published odds combos, ±0.001).
 * One equation cannot pin two unknowns, so the same match's '야구 승패' (two-way)
 * row supplies the home:away probability ratio; together they determine all three
 * odds. The two-way overround (1.14) was estimated from realized payouts
 * (a fixed-outcome bet across all games returns 1/overround).
 *
 * <p>The known slot always keeps {@code TOTAL_DIV} verbatim (승→HOME, 1→DRAW,
 * 패→AWAY); only the other two are estimates. DRAW_DIV estimated on 승/패 rows is
 * clamped to a plausible range and set to NULL outside it. Rows whose odds cannot
 * be computed (no matching 승패 row, guard failure) are skipped untouched — every
 * updated row is guaranteed non-null HOME_DIV and AWAY_DIV.
 */
@Service
@RequiredArgsConstructor
public class BaseballResultDivBackfillService {

    private static final String THREE_WAY_GAME_TYPE = "야구 승1패";
    private static final String TWO_WAY_GAME_TYPE = "야구 승패";
    private static final String WIN = "승";
    private static final String LOSS = "패";
    private static final String ONE_RUN_MARGIN = "1";

    /** 3-way overround: 1/승 + 1/무 + 1/패, verified from published odds. */
    private static final double THREE_WAY_OVERROUND = 1.15;
    /**
     * 2-way overround: 1/승 + 1/패. Measured directly from 24 published 승패 games
     * (2026-08-14~15, MLB 15 / NPB 6 / KBO 3): mean 1.13629, range 1.13516–1.13769, sd ≈ 0.0007,
     * flat across leagues and price levels. This replaces the earlier 1.14, which had been
     * reverse-engineered from realized payouts and ran ~0.4% high. See
     * {@link com.toto.baseballApi.baseballresult.domain.TwoWayOddsEstimator}, which holds the same
     * constant for the pre-game direction (3-way published price → 2-way price).
     */
    private static final double TWO_WAY_OVERROUND = TwoWayOddsEstimator.PUBLISHED_OVERROUND;
    /** Observed DRAW odds span 2.34–4.15; estimates outside this range become NULL. */
    private static final double DRAW_DIV_MIN = 2.0;
    private static final double DRAW_DIV_MAX = 9.9;
    /** DOUBLE(5,2) column bound; odds must also exceed 1.0 to be meaningful. */
    private static final double DIV_MIN_EXCLUSIVE = 1.0;
    private static final double DIV_MAX = 999.99;
    /**
     * Whether DRAW_DIV on 승/패 rows is estimated from the residual probability
     * (true) or left NULL (false). Flipping this later requires a one-off SQL reset
     * of rows whose DRAW_DIV is NULL/estimated, since populated rows are no longer
     * targeted by the backfill.
     */
    private static final boolean ESTIMATE_DRAW_ON_WIN_LOSS = true;

    private final BaseballResultRepository baseballResultRepository;

    public DivBackfillResult backfillDivs() {
        List<BaseballResult> targets = baseballResultRepository.findByGameType(THREE_WAY_GAME_TYPE).stream()
                .filter(result -> result.homeDiv() == null || result.awayDiv() == null)
                .toList();

        // The unique key also includes COND, so duplicates per match key are theoretically
        // possible — resolve deterministically by keeping the lowest id.
        Map<String, BaseballResult> twoWayByMatch = baseballResultRepository.findByGameType(TWO_WAY_GAME_TYPE).stream()
                .collect(Collectors.toMap(
                        BaseballResultDivBackfillService::matchKey,
                        Function.identity(),
                        (a, b) -> a.id() <= b.id() ? a : b));

        List<DivUpdate> updates = targets.stream()
                .map(target -> computeDivs(target, twoWayByMatch.get(matchKey(target))))
                .flatMap(Optional::stream)
                .toList();

        int updated = updates.isEmpty() ? 0 : baseballResultRepository.updateDivs(updates);
        return new DivBackfillResult(targets.size(), updated, targets.size() - updated);
    }

    private static String matchKey(BaseballResult result) {
        return String.join("|",
                String.valueOf(result.year()),
                String.valueOf(result.round()),
                result.tournament(),
                result.ymd(),
                result.tm(),
                result.home(),
                result.away());
    }

    private static Optional<DivUpdate> computeDivs(BaseballResult threeWay, BaseballResult twoWay) {
        if (twoWay == null) {
            return Optional.empty();
        }

        // Home:away probability ratio from the two-way market. Only the realized
        // outcome's odds are stored; the overround yields the other side.
        Double twoWayDiv = twoWay.totalDiv();
        if (twoWayDiv == null || twoWayDiv <= DIV_MIN_EXCLUSIVE) {
            return Optional.empty();
        }
        double qHome;
        double qAway;
        switch (twoWay.totalResult()) {
            case WIN -> {
                qHome = 1.0 / twoWayDiv;
                qAway = TWO_WAY_OVERROUND - qHome;
            }
            case LOSS -> {
                qAway = 1.0 / twoWayDiv;
                qHome = TWO_WAY_OVERROUND - qAway;
            }
            default -> {
                return Optional.empty();
            }
        }
        if (qHome <= 0 || qAway <= 0) {
            return Optional.empty();
        }
        double ratio = qHome / qAway;

        Double total = threeWay.totalDiv();
        if (total == null || total <= DIV_MIN_EXCLUSIVE) {
            return Optional.empty();
        }

        Double homeDiv;
        Double drawDiv;
        Double awayDiv;
        switch (threeWay.totalResult()) {
            case WIN -> {
                double pHome = 1.0 / total;
                double pAway = pHome / ratio;
                homeDiv = total;
                awayDiv = round2(1.0 / pAway);
                drawDiv = estimateDrawDiv(THREE_WAY_OVERROUND - pHome - pAway);
            }
            case LOSS -> {
                double pAway = 1.0 / total;
                double pHome = pAway * ratio;
                awayDiv = total;
                homeDiv = round2(1.0 / pHome);
                drawDiv = estimateDrawDiv(THREE_WAY_OVERROUND - pHome - pAway);
            }
            case ONE_RUN_MARGIN -> {
                double remaining = THREE_WAY_OVERROUND - 1.0 / total;
                if (remaining <= 0) {
                    return Optional.empty();
                }
                double pHome = remaining * ratio / (1.0 + ratio);
                double pAway = remaining / (1.0 + ratio);
                drawDiv = total;
                homeDiv = round2(1.0 / pHome);
                awayDiv = round2(1.0 / pAway);
            }
            default -> {
                return Optional.empty();
            }
        }

        // Updated rows must always carry home and away odds; skip the row entirely
        // (no partial write) when either estimate is implausible.
        if (!isValidDiv(homeDiv) || !isValidDiv(awayDiv)) {
            return Optional.empty();
        }
        return Optional.of(new DivUpdate(threeWay.id(), homeDiv, drawDiv, awayDiv));
    }

    private static Double estimateDrawDiv(double pDraw) {
        if (!ESTIMATE_DRAW_ON_WIN_LOSS || pDraw <= 0) {
            return null;
        }
        double drawDiv = round2(1.0 / pDraw);
        if (drawDiv < DRAW_DIV_MIN || drawDiv > DRAW_DIV_MAX) {
            return null;
        }
        return drawDiv;
    }

    private static boolean isValidDiv(Double div) {
        return div != null && div > DIV_MIN_EXCLUSIVE && div <= DIV_MAX;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
