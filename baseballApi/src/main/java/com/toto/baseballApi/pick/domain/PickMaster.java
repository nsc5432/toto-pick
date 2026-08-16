package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.util.List;

public record PickMaster(
        Integer id,
        Integer year,
        Integer round,
        String ymd,
        String userName,
        BigDecimal inputMoney,
        BigDecimal outputMoney,
        List<PickDetail> details) {
}
