package com.toto.baseballApi.pick.application;

import java.util.List;

/** {@code algorithmCodes} null/empty means "every algorithm that has picks in the range". */
public record PickKpiQuery(
        String bgngYmd,
        String endYmd,
        GroupBy groupBy,
        List<String> algorithmCodes) {

    public enum GroupBy {
        DAY, ROUND
    }
}
