import { useEffect, useState, useTransition } from "react";

import { fetchPickResults } from "@/entities/pick-result";
import type { PickResult } from "@/entities/pick-result";
import type { PageResponse } from "@/shared/api";
import { formatMoney, formatRate, isAbortError } from "@/shared/lib";

import "./PickResultGrid.css";

const PAGE_SIZE = 20;

export function PickResultGrid() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<PickResult> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();

  useEffect(() => {
    const controller = new AbortController();

    startTransition(async () => {
      try {
        const body = await fetchPickResults(page, PAGE_SIZE, controller.signal);
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
    <div className="pick-result-grid">
      <h1>시뮬레이션 결과</h1>

      {error && <p className="pick-result-grid__error">{error}</p>}

      <div className="pick-result-grid__table-wrap">
        <table className="pick-result-grid__table">
          <thead>
            <tr>
              <th>년도</th>
              <th>회차</th>
              <th>날짜</th>
              <th>사용자</th>
              <th>알고리즘</th>
              <th>투입금액</th>
              <th>적중금액</th>
              <th>수익률</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((row) => (
              <tr key={row.id}>
                <td>{row.year}</td>
                <td>{row.round}</td>
                <td>{row.ymd ?? "-"}</td>
                <td>{row.userName}</td>
                <td>{row.algorithmCode ?? "-"}</td>
                <td>{row.inputMoney.toLocaleString()}</td>
                <td>{formatMoney(row.outputMoney)}</td>
                <td>{formatRate(row.profitRate)}</td>
              </tr>
            ))}
            {!loading && data?.content.length === 0 && (
              <tr>
                <td colSpan={8} className="pick-result-grid__empty">
                  데이터가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="pick-result-grid__pager">
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
