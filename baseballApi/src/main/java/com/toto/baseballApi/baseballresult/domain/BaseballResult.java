package com.toto.baseballApi.baseballresult.domain;

public record BaseballResult(
        Integer id,
        Integer year,
        Integer round,
        String tournament,
        String ymd,
        String tm,
        String home,
        String away,
        String gameType,
        String cond,
        Double res1,
        Double res2,
        String totalResult,
        Double totalDiv) {
}
