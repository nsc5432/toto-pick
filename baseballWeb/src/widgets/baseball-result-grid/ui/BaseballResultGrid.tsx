import { useEffect, useState, useTransition } from "react";

import { fetchBaseballResults } from "@/entities/baseball-result";
import type { BaseballResult } from "@/entities/baseball-result";
import type { PageResponse } from "@/shared/api";
import { isAbortError } from "@/shared/lib";

import "./BaseballResultGrid.css";

const PAGE_SIZE = 20;

export function BaseballResultGrid() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<BaseballResult> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();

  useEffect(() => {
    const controller = new AbortController();

    startTransition(async () => {
      try {
        const body = await fetchBaseballResults(page, PAGE_SIZE, controller.signal);
        setData(body);
        setError(null);
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "데이터를 불러오지 못했습니다.");
        }
      }
    });

    return () => controller.abort();
  }, [page]);

  return (
    <div className="baseball-grid">
      <h1>Baseball Results</h1>

      {error && <p className="baseball-grid__error">{error}</p>}

      <div className="baseball-grid__table-wrap">
        <table className="baseball-grid__table">
          <thead>
            <tr>
              <th>년도</th>
              <th>회차</th>
              <th>대회</th>
              <th>경기일자</th>
              <th>경기시간</th>
              <th>홈</th>
              <th>원정</th>
              <th>게임방식</th>
              <th>조건</th>
              <th>결과1</th>
              <th>결과2</th>
              <th>결과</th>
              <th>배당</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((row) => (
              <tr key={row.id}>
                <td>{row.year}</td>
                <td>{row.round}</td>
                <td>{row.tournament}</td>
                <td>{row.ymd}</td>
                <td>{row.tm}</td>
                <td>{row.home}</td>
                <td>{row.away}</td>
                <td>{row.gameType}</td>
                <td>{row.cond ?? "-"}</td>
                <td>{row.res1}</td>
                <td>{row.res2 ?? "-"}</td>
                <td>{row.totalResult}</td>
                <td>{row.totalDiv}</td>
              </tr>
            ))}
            {!loading && data?.content.length === 0 && (
              <tr>
                <td colSpan={13} className="baseball-grid__empty">
                  데이터가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="baseball-grid__pager">
        <button
          type="button"
          onClick={() => setPage((p) => p - 1)}
          disabled={loading || !data || data.first}
        >
          이전
        </button>
        <span>
          {(data?.number ?? 0) + 1} / {Math.max(data?.totalPages ?? 1, 1)} 페이지
          {data ? ` (총 ${data.totalElements}건)` : ""}
        </span>
        <button
          type="button"
          onClick={() => setPage((p) => p + 1)}
          disabled={loading || !data || data.last}
        >
          다음
        </button>
      </div>
    </div>
  );
}
