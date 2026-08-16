import { useEffect, useMemo, useState, useTransition } from "react";

import { fetchPickKpis } from "@/entities/pick-result";
import type { AlgorithmKpi, KpiGroupBy, PickKpiResult } from "@/entities/pick-result";
import { formatMoney, formatRate, isAbortError } from "@/shared/lib";

import "./AlgorithmKpiDashboard.css";

export interface KpiDashboardQuery {
  bgngYmd: string;
  endYmd: string;
  algorithmCodes: string[];
}

interface AlgorithmKpiDashboardProps {
  query: KpiDashboardQuery;
  refreshKey: number;
}

type SortKey = "profitRate" | "hitRate" | "slipCount" | "inputTotal" | "outputTotal";

function bestCode(algorithms: AlgorithmKpi[], value: (a: AlgorithmKpi) => number | null): string | null {
  let best: AlgorithmKpi | null = null;
  let bestValue = Number.NEGATIVE_INFINITY;
  for (const algorithm of algorithms) {
    const v = value(algorithm);
    if (v !== null && v > bestValue) {
      best = algorithm;
      bestValue = v;
    }
  }
  return best?.algorithmCode ?? null;
}

export function AlgorithmKpiDashboard({ query, refreshKey }: AlgorithmKpiDashboardProps) {
  const [groupBy, setGroupBy] = useState<KpiGroupBy>("day");
  const [data, setData] = useState<PickKpiResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>("profitRate");
  const [loading, startTransition] = useTransition();

  useEffect(() => {
    const controller = new AbortController();

    startTransition(async () => {
      try {
        const body = await fetchPickKpis(
          {
            bgngYmd: query.bgngYmd,
            endYmd: query.endYmd,
            groupBy,
            algorithmCodes: query.algorithmCodes,
          },
          controller.signal,
        );
        setData(body);
        setError(null);
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "KPI를 불러오지 못했습니다.");
        }
      }
    });

    return () => controller.abort();
  }, [query, groupBy, refreshKey]);

  const algorithms = useMemo(() => data?.algorithms ?? [], [data]);

  const bestProfitCode = useMemo(
    () => bestCode(algorithms, (a) => a.summary.profitRate),
    [algorithms],
  );
  const bestHitCode = useMemo(() => bestCode(algorithms, (a) => a.summary.hitRate), [algorithms]);

  const sortedAlgorithms = useMemo(() => {
    const value = (a: AlgorithmKpi): number => {
      const v = a.summary[sortKey];
      return v === null ? Number.NEGATIVE_INFINITY : v;
    };
    return [...algorithms].sort((a, b) => value(b) - value(a));
  }, [algorithms, sortKey]);

  // 기간 피벗: 전체 알고리즘의 periodKey 합집합(오름차순)을 행으로 쓴다.
  const periodRows = useMemo(() => {
    const keys = new Set<string>();
    for (const algorithm of algorithms) {
      for (const period of algorithm.periods) {
        keys.add(period.periodKey);
      }
    }
    const sortedKeys = [...keys].sort();
    return sortedKeys.map((key) => ({
      key,
      cells: algorithms.map(
        (algorithm) => algorithm.periods.find((p) => p.periodKey === key) ?? null,
      ),
    }));
  }, [algorithms]);

  const headerLabel = (key: SortKey, label: string) => (
    <th
      className={`algorithm-kpi__sortable${sortKey === key ? " algorithm-kpi__sortable--active" : ""}`}
      onClick={() => setSortKey(key)}
    >
      {label}
      {sortKey === key ? " ▼" : ""}
    </th>
  );

  return (
    <section className="algorithm-kpi">
      <h1>알고리즘별 KPI 비교</h1>
      <p className="algorithm-kpi__range">
        조회 기간: {query.bgngYmd} ~ {query.endYmd}
      </p>

      {error && <p className="algorithm-kpi__error">{error}</p>}

      {!error && algorithms.length === 0 && !loading && (
        <p className="algorithm-kpi__empty">
          기간 내 KPI 데이터가 없습니다. 먼저 시뮬레이션을 실행하세요.
        </p>
      )}

      {algorithms.length > 0 && (
        <>
          <div className="algorithm-kpi__cards">
            {algorithms.map((algorithm) => {
              const isBestProfit = algorithm.algorithmCode === bestProfitCode;
              const isBestHit = algorithm.algorithmCode === bestHitCode;
              return (
                <div
                  key={algorithm.algorithmCode}
                  className={`algorithm-kpi__card${isBestProfit ? " algorithm-kpi__card--best" : ""}`}
                >
                  <div className="algorithm-kpi__card-header">
                    <span className="algorithm-kpi__card-name">{algorithm.algorithmName}</span>
                    <span className="algorithm-kpi__card-code">{algorithm.algorithmCode}</span>
                  </div>
                  <div className="algorithm-kpi__badges">
                    {isBestProfit && <span className="algorithm-kpi__badge">수익률 1위</span>}
                    {isBestHit && (
                      <span className="algorithm-kpi__badge algorithm-kpi__badge--hit">
                        적중률 1위
                      </span>
                    )}
                  </div>
                  <dl className="algorithm-kpi__stats">
                    <div>
                      <dt>수익률</dt>
                      <dd className="algorithm-kpi__stat-main">
                        {formatRate(algorithm.summary.profitRate)}
                      </dd>
                    </div>
                    <div>
                      <dt>적중률</dt>
                      <dd>{formatRate(algorithm.summary.hitRate)}</dd>
                    </div>
                    <div>
                      <dt>조합/적중</dt>
                      <dd>
                        {algorithm.summary.slipCount} / {algorithm.summary.hitCount}
                      </dd>
                    </div>
                    <div>
                      <dt>투입/회수</dt>
                      <dd>
                        {formatMoney(algorithm.summary.inputTotal)} /{" "}
                        {formatMoney(algorithm.summary.outputTotal)}
                      </dd>
                    </div>
                  </dl>
                </div>
              );
            })}
          </div>

          <h2>기간 전체 비교</h2>
          <div className="algorithm-kpi__table-wrap">
            <table className="algorithm-kpi__table">
              <thead>
                <tr>
                  <th>알고리즘</th>
                  {headerLabel("profitRate", "수익률")}
                  {headerLabel("hitRate", "적중률")}
                  {headerLabel("slipCount", "조합수")}
                  <th>적중수</th>
                  {headerLabel("inputTotal", "투입금액")}
                  {headerLabel("outputTotal", "회수금액")}
                </tr>
              </thead>
              <tbody>
                {sortedAlgorithms.map((algorithm) => (
                  <tr
                    key={algorithm.algorithmCode}
                    className={
                      algorithm.algorithmCode === bestProfitCode
                        ? "algorithm-kpi__row--best"
                        : undefined
                    }
                  >
                    <td className="algorithm-kpi__name-cell">
                      {algorithm.algorithmName} ({algorithm.algorithmCode})
                    </td>
                    <td>{formatRate(algorithm.summary.profitRate)}</td>
                    <td>{formatRate(algorithm.summary.hitRate)}</td>
                    <td>{algorithm.summary.slipCount}</td>
                    <td>{algorithm.summary.hitCount}</td>
                    <td>{formatMoney(algorithm.summary.inputTotal)}</td>
                    <td>{formatMoney(algorithm.summary.outputTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="algorithm-kpi__period-header">
            <h2>기간별 수익률</h2>
            <div className="algorithm-kpi__toggle">
              <button
                type="button"
                className={groupBy === "day" ? "algorithm-kpi__toggle--active" : undefined}
                onClick={() => setGroupBy("day")}
              >
                일자별
              </button>
              <button
                type="button"
                className={groupBy === "round" ? "algorithm-kpi__toggle--active" : undefined}
                onClick={() => setGroupBy("round")}
              >
                회차별
              </button>
            </div>
          </div>
          <div className="algorithm-kpi__table-wrap">
            <table className="algorithm-kpi__table">
              <thead>
                <tr>
                  <th rowSpan={2}>{groupBy === "day" ? "일자" : "년도-회차"}</th>
                  {algorithms.map((algorithm) => (
                    <th key={algorithm.algorithmCode} colSpan={2}>
                      {algorithm.algorithmName}
                    </th>
                  ))}
                </tr>
                <tr>
                  {algorithms.map((algorithm) => (
                    <SubHeaders key={algorithm.algorithmCode} />
                  ))}
                </tr>
              </thead>
              <tbody>
                {periodRows.map((row) => {
                  const rowBest = bestPeriodIndex(row.cells);
                  return (
                    <tr key={row.key}>
                      <td>{row.key}</td>
                      {row.cells.map((cell, index) => (
                        <PeriodCells
                          key={algorithms[index].algorithmCode}
                          profitRate={cell?.profitRate ?? null}
                          hitRate={cell?.hitRate ?? null}
                          empty={cell === null}
                          best={index === rowBest}
                        />
                      ))}
                    </tr>
                  );
                })}
                {periodRows.length === 0 && (
                  <tr>
                    <td colSpan={1 + algorithms.length * 2} className="algorithm-kpi__empty-cell">
                      기간 내 데이터가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

function SubHeaders() {
  return (
    <>
      <th className="algorithm-kpi__sub-header">수익률</th>
      <th className="algorithm-kpi__sub-header">적중률</th>
    </>
  );
}

interface PeriodCellsProps {
  profitRate: number | null;
  hitRate: number | null;
  empty: boolean;
  best: boolean;
}

function PeriodCells({ profitRate, hitRate, empty, best }: PeriodCellsProps) {
  const profitClass = [
    best ? "algorithm-kpi__cell--best" : "",
    profitRate !== null && profitRate > 0 ? "algorithm-kpi__cell--positive" : "",
    profitRate !== null && profitRate < 0 ? "algorithm-kpi__cell--negative" : "",
  ]
    .filter(Boolean)
    .join(" ");
  return (
    <>
      <td className={profitClass || undefined}>{empty ? "-" : formatRate(profitRate)}</td>
      <td>{empty ? "-" : formatRate(hitRate)}</td>
    </>
  );
}

function bestPeriodIndex(cells: ({ profitRate: number | null } | null)[]): number {
  let bestIndex = -1;
  let bestValue = Number.NEGATIVE_INFINITY;
  cells.forEach((cell, index) => {
    if (cell?.profitRate != null && cell.profitRate > bestValue) {
      bestValue = cell.profitRate;
      bestIndex = index;
    }
  });
  return bestIndex;
}
