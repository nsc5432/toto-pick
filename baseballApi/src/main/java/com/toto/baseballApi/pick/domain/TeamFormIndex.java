package com.toto.baseballApi.pick.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/**
 * Every team's 2-way appearances, indexed once so recent form can be read back as of any date.
 *
 * <p>Two reasons this exists rather than each algorithm re-deriving form from a list of games.
 *
 * <p>The first is correctness. Every lookup takes the {@code beforeYmd} it must not see past, so
 * "only games before the day being picked" is enforced by the index itself instead of by each
 * caller remembering to pre-filter. Lookahead is the failure mode that makes a backtest look
 * brilliant and a live run look nothing like it, and it is far too easy to introduce by accident.
 *
 * <p>The second is that a parameter sweep re-runs the same days hundreds of times. Re-grouping and
 * re-sorting the full history on every day of every candidate is what makes a search take hours;
 * built once and shared, a form lookup is a binary search.
 */
public final class TeamFormIndex {

    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";
    private static final TeamFormIndex EMPTY = new TeamFormIndex(Map.of());

    /** One team's participation in one game, from that team's point of view. */
    private record Appearance(String ymd, String tm, boolean win) {
    }

    /** Per team, appearances sorted ascending by (ymd, tm) — so any prefix is "before some date". */
    private final Map<String, List<Appearance>> appearancesByTeam;

    private TeamFormIndex(Map<String, List<Appearance>> appearancesByTeam) {
        this.appearancesByTeam = appearancesByTeam;
    }

    public static TeamFormIndex empty() {
        return EMPTY;
    }

    /** Builds from 2-way ("야구 승패") games; input order does not matter. */
    public static TeamFormIndex build(Collection<BaseballResult> twoWayGames) {
        if (twoWayGames == null || twoWayGames.isEmpty()) {
            return EMPTY;
        }

        Map<String, List<Appearance>> byTeam = new HashMap<>();
        for (BaseballResult game : twoWayGames) {
            byTeam.computeIfAbsent(game.home(), k -> new ArrayList<>())
                    .add(new Appearance(game.ymd(), game.tm(), HOME_WIN.equals(game.totalResult())));
            byTeam.computeIfAbsent(game.away(), k -> new ArrayList<>())
                    .add(new Appearance(game.ymd(), game.tm(), AWAY_WIN.equals(game.totalResult())));
        }

        Comparator<Appearance> chronological =
                Comparator.comparing(Appearance::ymd).thenComparing(Appearance::tm);
        byTeam.values().forEach(appearances -> appearances.sort(chronological));
        return new TeamFormIndex(byTeam);
    }

    public Set<String> teams() {
        return appearancesByTeam.keySet();
    }

    /**
     * Win percentage (0..100, rounded) over the team's {@code num} most recent appearances strictly
     * before {@code beforeYmd}; {@code null} when the team has none — an unknown team has no form,
     * which is not the same as a 0% one and must not be ranked as though it were.
     */
    public Long winPercent(String team, String beforeYmd, int num) {
        if (num < 1) {
            throw new IllegalArgumentException("num must be >= 1");
        }
        List<Appearance> appearances = appearancesByTeam.get(team);
        if (appearances == null) {
            return null;
        }

        int end = firstIndexOnOrAfter(appearances, beforeYmd);
        if (end == 0) {
            return null;
        }
        int start = Math.max(0, end - num);

        int wins = 0;
        for (int i = start; i < end; i++) {
            if (appearances.get(i).win()) {
                wins++;
            }
        }
        return Math.round(wins * 100.0 / (end - start));
    }

    /** Every team's {@link #winPercent} as of the same date, teams without form omitted. */
    public Map<String, Long> winPercents(String beforeYmd, int num) {
        Map<String, Long> percents = new LinkedHashMap<>();
        appearancesByTeam.keySet().forEach(team -> {
            Long percent = winPercent(team, beforeYmd, num);
            if (percent != null) {
                percents.put(team, percent);
            }
        });
        return percents;
    }

    /** Binary search on ymd alone — appearances on {@code ymd} itself are the future for that day. */
    private static int firstIndexOnOrAfter(List<Appearance> appearances, String ymd) {
        int low = 0;
        int high = appearances.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (appearances.get(mid).ymd().compareTo(ymd) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
