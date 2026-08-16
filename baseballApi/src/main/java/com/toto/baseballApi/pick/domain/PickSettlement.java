package com.toto.baseballApi.pick.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;

/** Settlement math shared by manual settlement and simulation — kept in one place so payouts never diverge. */
public final class PickSettlement {

    private PickSettlement() {
    }

    public static BigDecimal settle(
            List<PickDetail> details, Map<Integer, BaseballResult> actualResultsById, BigDecimal inputMoney) {
        BigDecimal combinedOdds = BigDecimal.ONE;
        for (PickDetail detail : details) {
            BaseballResult actual = actualResultsById.get(detail.resultId());
            if (actual == null || !actual.totalResult().equals(detail.totalResult())) {
                return BigDecimal.ZERO;
            }
            combinedOdds = combinedOdds.multiply(BigDecimal.valueOf(actual.totalDiv()));
        }
        // "소수 둘째에서 올림" — round the combined odds UP at the 2nd decimal place.
        combinedOdds = combinedOdds.setScale(2, RoundingMode.CEILING);
        return combinedOdds.multiply(inputMoney).setScale(0, RoundingMode.HALF_UP);
    }
}
