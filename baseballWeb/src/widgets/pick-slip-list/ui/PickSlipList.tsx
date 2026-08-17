import { useEffect, useMemo, useState, useTransition } from "react";

import { fetchAlgorithms, fetchPickSlips } from "@/entities/pick-result";
import type { PickAlgorithmInfo, PickSlip, PickSlipLeg } from "@/entities/pick-result";
import { formatMoney, isAbortError } from "@/shared/lib";

import "./PickSlipList.css";

export interface SlipListQuery {
  bgngYmd: string;
  endYmd: string;
  algorithmCodes: string[];
}

interface PickSlipListProps {
  query: SlipListQuery;
  refreshKey: number;
}

const ALL = "__ALL__";

/** 슬립에 포함된 각 레그의 배당을 곱한 최종 배당. 배당이 없는 레그가 하나라도 있으면 계산 불가. */
function combinedOdds(legs: PickSlipLeg[]): number | null {
  let product = 1;
  for (const leg of legs) {
    if (leg.backedOdds === null) {
      return null;
    }
    product *= leg.backedOdds;
  }
  return product;
}

/** 픽/결과("승"·"1"·"패")를 해당 팀 이름으로 풀어쓴다. */
function sideLabel(side: string | null, leg: PickSlipLeg): string {
  if (side === "승") {
    return `${leg.home ?? "홈"} 승`;
  }
  if (side === "패") {
    return `${leg.away ?? "원정"} 승`;
  }
  if (side === "1") {
    return "무승부";
  }
  return "-";
}

export function PickSlipList({ query, refreshKey }: PickSlipListProps) {
  const [slips, setSlips] = useState<PickSlip[]>([]);
  const [algorithms, setAlgorithms] = useState<PickAlgorithmInfo[]>([]);
  const [selectedCode, setSelectedCode] = useState<string>(ALL);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();

  useEffect(() => {
    const controller = new AbortController();
    fetchAlgorithms(controller.signal)
      .then(setAlgorithms)
      .catch(() => {
        // 이름 매핑용 부가 정보 — 실패해도 코드로 표시하면 된다.
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    startTransition(async () => {
      try {
        const body = await fetchPickSlips(
          {
            bgngYmd: query.bgngYmd,
            endYmd: query.endYmd,
            algorithmCodes: query.algorithmCodes,
          },
          controller.signal,
        );
        setSlips(body);
        setError(null);
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "조합 내역을 불러오지 못했습니다.");
        }
      }
    });

    return () => controller.abort();
  }, [query, refreshKey]);

  const nameByCode = useMemo(
    () => new Map(algorithms.map((a) => [a.code, a.name])),
    [algorithms],
  );

  const codesInData = useMemo(
    () => [...new Set(slips.map((s) => s.algorithmCode))].sort(),
    [slips],
  );

  const visibleSlips = useMemo(
    () => (selectedCode === ALL ? slips : slips.filter((s) => s.algorithmCode === selectedCode)),
    [slips, selectedCode],
  );

  const hitCount = useMemo(() => visibleSlips.filter((s) => s.hit).length, [visibleSlips]);

  return (
    <section className="pick-slip-list">
      <div className="pick-slip-list__header">
        <h2>픽 조합 내역</h2>
        <select
          className="pick-slip-list__filter"
          value={selectedCode}
          onChange={(e) => setSelectedCode(e.target.value)}
        >
          <option value={ALL}>전체 알고리즘</option>
          {codesInData.map((code) => (
            <option key={code} value={code}>
              {nameByCode.get(code) ?? code} ({code})
            </option>
          ))}
        </select>
        <span className="pick-slip-list__summary">
          조합 {visibleSlips.length}건 / 적중 {hitCount}건
        </span>
      </div>

      {error && <p className="pick-slip-list__error">{error}</p>}

      {!error && visibleSlips.length === 0 && !loading && (
        <p className="pick-slip-list__empty">
          기간 내 조합 내역이 없습니다. 먼저 시뮬레이션을 실행하세요.
        </p>
      )}

      {visibleSlips.length > 0 && (
        <div className="pick-slip-list__table-wrap">
          <table className="pick-slip-list__table">
            <thead>
              <tr>
                <th>일자</th>
                <th>회차</th>
                <th>알고리즘</th>
                <th>경기</th>
                <th>픽</th>
                <th>배당</th>
                <th>최종배당</th>
                <th>경기결과</th>
                <th>레그</th>
                <th>투입금액</th>
                <th>예상적중금액</th>
                <th>회수금액</th>
                <th>적중</th>
              </tr>
            </thead>
            <tbody>
              {visibleSlips.map((slip) => {
                const slipCombinedOdds = combinedOdds(slip.legs);
                return slip.legs.map((leg, legIndex) => (
                  <tr
                    key={`${slip.id}-${leg.resultId}`}
                    className={slip.hit ? "pick-slip-list__row--hit" : undefined}
                  >
                    {legIndex === 0 && (
                      <>
                        <td rowSpan={slip.legs.length}>{slip.ymd ?? "-"}</td>
                        <td rowSpan={slip.legs.length}>
                          {slip.year}-{slip.round}
                        </td>
                        <td rowSpan={slip.legs.length} className="pick-slip-list__algo-cell">
                          {nameByCode.get(slip.algorithmCode) ?? slip.algorithmCode}
                        </td>
                      </>
                    )}
                    <td className="pick-slip-list__match-cell">
                      {leg.home ?? "?"} vs {leg.away ?? "?"}
                    </td>
                    <td className="pick-slip-list__pick-cell">{sideLabel(leg.predicted, leg)}</td>
                    <td>{leg.backedOdds === null ? "-" : leg.backedOdds.toFixed(2)}</td>
                    {legIndex === 0 && (
                      <td rowSpan={slip.legs.length} className="pick-slip-list__combined-odds-cell">
                        {slipCombinedOdds === null ? "-" : slipCombinedOdds.toFixed(2)}
                      </td>
                    )}
                    <td>{sideLabel(leg.actualResult, leg)}</td>
                    <td
                      className={
                        leg.legHit ? "pick-slip-list__leg--hit" : "pick-slip-list__leg--miss"
                      }
                    >
                      {leg.legHit ? "적중" : "미적중"}
                    </td>
                    {legIndex === 0 && (
                      <>
                        <td rowSpan={slip.legs.length}>{formatMoney(slip.inputMoney)}</td>
                        <td rowSpan={slip.legs.length}>
                          {formatMoney(
                            slipCombinedOdds === null
                              ? null
                              : Math.round(slip.inputMoney * slipCombinedOdds),
                            "-",
                          )}
                        </td>
                        <td rowSpan={slip.legs.length}>{formatMoney(slip.outputMoney)}</td>
                        <td
                          rowSpan={slip.legs.length}
                          className={
                            slip.hit ? "pick-slip-list__leg--hit" : "pick-slip-list__leg--miss"
                          }
                        >
                          {slip.hit ? "적중" : slip.outputMoney === null ? "미정산" : "미적중"}
                        </td>
                      </>
                    )}
                  </tr>
                ));
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
