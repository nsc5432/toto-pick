package com.toto.baseballApi.baseballresult.presentation.dto;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

public record BaseballResultResponse(
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

    public static BaseballResultResponse from(BaseballResult baseballResult) {
        return new BaseballResultResponse(
                baseballResult.id(),
                baseballResult.year(),
                baseballResult.round(),
                baseballResult.tournament(),
                baseballResult.ymd(),
                baseballResult.tm(),
                baseballResult.home(),
                baseballResult.away(),
                baseballResult.gameType(),
                baseballResult.cond(),
                baseballResult.res1(),
                baseballResult.res2(),
                baseballResult.totalResult(),
                baseballResult.totalDiv());
    }
}
