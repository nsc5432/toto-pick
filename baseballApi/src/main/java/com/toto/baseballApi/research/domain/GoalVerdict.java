package com.toto.baseballApi.research.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The pipeline's final stage answer — did this candidate clear the declared target, and if not,
 * exactly which requirements it missed. The failed checks are the input to the next hypothesis:
 * missing on sample size calls for a looser filter, missing on profit rate for a different signal.
 */
public record GoalVerdict(boolean achieved, List<GoalCheck> checks) {

    public GoalVerdict {
        checks = List.copyOf(checks);
    }

    public List<GoalCheck> failedChecks() {
        return checks.stream().filter(check -> !check.passed()).toList();
    }

    /** One-line summary of what went wrong, e.g. {@code "수익률(요구 >= 0.1000, 실측 0.0412)"}. */
    public String failureSummary() {
        return failedChecks().stream()
                .map(check -> "%s(요구 %s, 실측 %s)".formatted(check.name(), check.requirement(), check.actual()))
                .collect(Collectors.joining("; "));
    }
}
