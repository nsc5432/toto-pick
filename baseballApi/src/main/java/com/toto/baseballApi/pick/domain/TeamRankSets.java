package com.toto.baseballApi.pick.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The best- and worst-form teams as of one date, by DENSE_RANK over rounded win percentage.
 *
 * <p>Ranking is on the <em>distinct</em> percentages, not on teams, so ties widen a set rather than
 * truncating it — five teams all on 60% occupy one rank, matching SQL {@code DENSE_RANK} semantics.
 * Cutting at the fifth team instead would make membership depend on the tie-break order, which is
 * arbitrary.
 *
 * <p>Shared by every form-filtered family so the two markets rank teams identically: the whole point
 * of a 승패 variant is to isolate the market switch, which only works if selection differs in exactly
 * one place. Reads form through {@link TeamFormIndex}, so the date cutoff — and with it the
 * impossibility of lookahead — is inherited rather than re-implemented.
 */
record TeamRankSets(Set<String> top, Set<String> bottom) {

    /** 상·하위로 볼 DENSE_RANK 순위 수 — 필터가 얼마나 많은 팀을 후보로 인정할지. */
    static final String RANK_LIMIT = "rankLimit";

    /** The original hard-coded value, kept as the default so existing behaviour is the midpoint. */
    static final double DEFAULT_RANK_LIMIT = 5;

    /**
     * @param rankLimit how many distinct ranks count as "top" and as "bottom". This is the family's
     *                  strongest sample-size lever: it decides how many teams are eligible at all,
     *                  and therefore how many games can ever become candidates. It was a baked-in 5
     *                  until it was measured, which meant it was the one threshold in the family that
     *                  was never validated
     */
    static TeamRankSets of(TeamFormIndex formIndex, String ymd, int num, int rankLimit) {
        if (rankLimit < 1) {
            throw new IllegalArgumentException("rankLimit must be >= 1: " + rankLimit);
        }
        Map<String, Long> percentByTeam = formIndex.winPercents(ymd, num);

        List<Long> distinctDesc = percentByTeam.values().stream().distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        Set<Long> topPercents = new HashSet<>(
                distinctDesc.subList(0, Math.min(rankLimit, distinctDesc.size())));
        Set<Long> bottomPercents = new HashSet<>(
                distinctDesc.subList(Math.max(0, distinctDesc.size() - rankLimit), distinctDesc.size()));

        Set<String> top = new HashSet<>();
        Set<String> bottom = new HashSet<>();
        percentByTeam.forEach((team, percent) -> {
            if (topPercents.contains(percent)) {
                top.add(team);
            }
            if (bottomPercents.contains(percent)) {
                bottom.add(team);
            }
        });
        return new TeamRankSets(top, bottom);
    }

    boolean isTop(String team) {
        return top.contains(team);
    }

    boolean isBottom(String team) {
        return bottom.contains(team);
    }
}
