package com.toto.baseballApi.research.domain;

/**
 * One pass/fail line of a {@link GoalVerdict}, kept as rendered text so a report reads the same in
 * the API, the leaderboard, and an agent's transcript: {@code 수익률  요구 >= 0.1000  실측 0.1372  PASS}.
 */
public record GoalCheck(String name, String requirement, String actual, boolean passed) {
}
