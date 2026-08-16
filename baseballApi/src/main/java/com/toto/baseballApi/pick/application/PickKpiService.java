package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickKpiRow;
import com.toto.baseballApi.pick.domain.PickMasterRepository;

import lombok.RequiredArgsConstructor;

/**
 * Aggregates the persisted simulation picks into per-algorithm KPIs: an overall summary for the
 * range plus per-period (day or round) buckets. Grouping is done in Java over a lightweight
 * read model, consistent with the rest of the codebase (no SQL GROUP BY).
 */
@Service
@RequiredArgsConstructor
public class PickKpiService {

    private final PickMasterRepository pickMasterRepository;
    private final List<PickAlgorithm> algorithms;

    public PickKpiResult kpis(PickKpiQuery query) {
        if (query.bgngYmd().compareTo(query.endYmd()) > 0) {
            throw new IllegalArgumentException("bgngYmd must not be after endYmd");
        }

        List<PickKpiRow> rows = pickMasterRepository.findKpiRows(
                PickSimulationService.SIMULATION_USER_NAME,
                query.algorithmCodes(), query.bgngYmd(), query.endYmd());

        Map<String, String> namesByCode = algorithms.stream()
                .collect(Collectors.toMap(PickAlgorithm::code, PickAlgorithm::name));

        Map<String, List<PickKpiRow>> rowsByAlgorithm = rows.stream()
                .collect(Collectors.groupingBy(PickKpiRow::algorithmCode, TreeMap::new, Collectors.toList()));

        List<PickKpiResult.AlgorithmKpi> algorithmKpis = rowsByAlgorithm.entrySet().stream()
                .map(entry -> new PickKpiResult.AlgorithmKpi(
                        entry.getKey(),
                        namesByCode.getOrDefault(entry.getKey(), entry.getKey()),
                        summarize(entry.getValue()),
                        periods(entry.getValue(), query.groupBy())))
                .toList();

        return new PickKpiResult(query.groupBy(), algorithmKpis);
    }

    private PickKpiResult.Kpi summarize(List<PickKpiRow> rows) {
        Totals totals = accumulate(rows);
        return new PickKpiResult.Kpi(
                totals.slipCount, totals.hitCount, totals.inputTotal, totals.outputTotal);
    }

    private record PeriodKey(String key, String ymd, Integer year, Integer round) {
    }

    private List<PickKpiResult.PeriodKpi> periods(List<PickKpiRow> rows, PickKpiQuery.GroupBy groupBy) {
        Map<PeriodKey, List<PickKpiRow>> byPeriod = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> toPeriodKey(row, groupBy), LinkedHashMap::new, Collectors.toList()));

        List<PickKpiResult.PeriodKpi> periods = new ArrayList<>();
        byPeriod.forEach((period, periodRows) -> {
            Totals totals = accumulate(periodRows);
            periods.add(new PickKpiResult.PeriodKpi(
                    period.key(), period.ymd(), period.year(), period.round(),
                    totals.slipCount, totals.hitCount, totals.inputTotal, totals.outputTotal));
        });
        periods.sort(Comparator.comparing(PickKpiResult.PeriodKpi::periodKey));
        return periods;
    }

    private PeriodKey toPeriodKey(PickKpiRow row, PickKpiQuery.GroupBy groupBy) {
        if (groupBy == PickKpiQuery.GroupBy.ROUND) {
            return new PeriodKey(row.year() + "-" + row.round(), null, row.year(), row.round());
        }
        return new PeriodKey(row.ymd(), row.ymd(), row.year(), row.round());
    }

    private static final class Totals {
        int slipCount;
        int hitCount;
        BigDecimal inputTotal = BigDecimal.ZERO;
        BigDecimal outputTotal = BigDecimal.ZERO;
    }

    private Totals accumulate(List<PickKpiRow> rows) {
        Totals totals = new Totals();
        for (PickKpiRow row : rows) {
            totals.slipCount++;
            if (row.outputMoney() != null && row.outputMoney().compareTo(BigDecimal.ZERO) > 0) {
                totals.hitCount++;
            }
            totals.inputTotal = totals.inputTotal.add(row.inputMoney());
            if (row.outputMoney() != null) {
                totals.outputTotal = totals.outputTotal.add(row.outputMoney());
            }
        }
        return totals;
    }
}
