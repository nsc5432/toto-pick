import { useEffect, useMemo, useState, useTransition } from "react";

import { fetchForwardPicks, fetchScheduleDates } from "@/entities/forward-pick";
import type { ForwardPick, ForwardPickLeg } from "@/entities/forward-pick";
import { formatMoney, isAbortError } from "@/shared/lib";

import "./ForwardPickList.css";

interface ForwardPickListProps {
  refreshKey: number;
}

const ALL = "__ALL__";

/** 픽/결과를 팀 이름으로 풀어쓴다. 2면 시장은 승/패뿐이고 무(1)는 승1패에만 있다. */
function sideLabel(side: string | null, leg: ForwardPickLeg): string {
  switch (side) {
    case "승":
      return `${leg.home} 승`;
    case "패":
      return `${leg.away} 승`;
    case "1":
      return "1점차";
    case null:
      return "미정산";
    default:
      return side;
  }
}

function statusLabel(pick: ForwardPick): string {
  if (pick.status === "PENDING") {
    return "경기 전/대기";
  }
  if (pick.status === "VOID") {
    return "무효";
  }
  return pick.hit ? "적중" : "미적중";
}

export function ForwardPickList({ refreshKey }: ForwardPickListProps) {
  const [picks, setPicks] = useState<ForwardPick[]>([]);
  const [scheduleDates, setScheduleDates] = useState<string[]>([]);
  const [selectedCode, setSelectedCode] = useState<string>(ALL);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();

  useEffect(() => {
    const controller = new AbortController();
    startTransition(async () => {
      try {
        setPicks(await fetchForwardPicks({}, controller.signal));
        setError(null);
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "사전 픽을 불러오지 못했습니다.");
        }
      }
    });
    return () => controller.abort();
  }, [refreshKey]);

  useEffect(() => {
    const controller = new AbortController();
    fetchScheduleDates(controller.signal)
      .then(setScheduleDates)
      .catch(() => {
        // 적재 현황은 부가 정보 — 실패해도 픽 목록은 보여준다.
      });
    return () => controller.abort();
  }, [refreshKey]);

  const codesInData = useMemo(
    () => [...new Set(picks.map((p) => p.algorithmCode))].sort(),
    [picks],
  );

  const visible = useMemo(
    () => (selectedCode === ALL ? picks : picks.filter((p) => p.algorithmCode === selectedCode)),
    [picks, selectedCode],
  );

  return (
    <section className="forward-picks">
      <div className="forward-picks__header">
        <h2>사전 픽 기록</h2>
        <select value={selectedCode} onChange={(e) => setSelectedCode(e.target.value)}>
          <option value={ALL}>전체 알고리즘</option>
          {codesInData.map((code) => (
            <option key={code} value={code}>
              {code}
            </option>
          ))}
        </select>
        <span className="forward-picks__summary">
          {visible.length}건 · <code>match_schedule</code> 적재:{" "}
          {scheduleDates.length > 0 ? scheduleDates.join(", ") : "없음"}
        </span>
      </div>

      {error && <p className="forward-picks__error">{error}</p>}

      {!error && visible.length === 0 && !loading && (
        <p className="forward-picks__empty">기록된 사전 픽이 없습니다.</p>
      )}

      {visible.length > 0 && (
        <div className="forward-picks__table-wrap">
          <table className="forward-picks__table">
            <thead>
              <tr>
                <th>경기일</th>
                <th>버킷</th>
                <th>슬립</th>
                <th>알고리즘</th>
                <th>경기</th>
                <th>시각</th>
                <th>시장</th>
                <th>픽</th>
                <th>베팅시점 배당</th>
                <th>결과</th>
                <th>레그</th>
                <th>조합배당</th>
                <th>투입</th>
                <th>회수</th>
                <th>상태</th>
                <th>기록 시각</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((pick) =>
                pick.legs.map((leg, legIndex) => (
                  <tr
                    key={`${pick.id}-${leg.legNo}`}
                    className={pick.hit ? "forward-picks__row--hit" : undefined}
                  >
                    {legIndex === 0 && (
                      <>
                        <td rowSpan={pick.legs.length}>{pick.ymd}</td>
                        <td rowSpan={pick.legs.length}>{pick.bucket}</td>
                        <td rowSpan={pick.legs.length}>#{pick.slipNo}</td>
                        <td rowSpan={pick.legs.length} className="forward-picks__left">
                          {pick.algorithmName}
                        </td>
                      </>
                    )}
                    <td className="forward-picks__left">
                      {leg.home} vs {leg.away}
                    </td>
                    <td>{leg.tm}</td>
                    <td>{leg.gameType}</td>
                    <td className="forward-picks__pick">
                      {sideLabel(leg.predictedTotalResult, leg)}
                    </td>
                    <td>{leg.backedOdds.toFixed(2)}</td>
                    <td>{sideLabel(leg.legResult, leg)}</td>
                    <td
                      className={
                        leg.legHit === null
                          ? undefined
                          : leg.legHit
                            ? "forward-picks__hit"
                            : "forward-picks__miss"
                      }
                    >
                      {leg.legHit === null ? "-" : leg.legHit ? "적중" : "미적중"}
                    </td>
                    {legIndex === 0 && (
                      <>
                        <td rowSpan={pick.legs.length}>
                          {pick.combinedOdds === null ? "-" : pick.combinedOdds.toFixed(2)}
                        </td>
                        <td rowSpan={pick.legs.length}>{formatMoney(pick.inputMoney)}</td>
                        <td rowSpan={pick.legs.length}>{formatMoney(pick.outputMoney)}</td>
                        <td
                          rowSpan={pick.legs.length}
                          className={
                            pick.status !== "SETTLED"
                              ? undefined
                              : pick.hit
                                ? "forward-picks__hit"
                                : "forward-picks__miss"
                          }
                        >
                          {statusLabel(pick)}
                        </td>
                        {/* 기록 시각이 경기 시각보다 앞선다는 것이 이 표의 증거력 전부다. */}
                        <td rowSpan={pick.legs.length} className="forward-picks__created">
                          {pick.createdAt.replace("T", " ").slice(0, 16)}
                        </td>
                      </>
                    )}
                  </tr>
                )),
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
