import { useState, useTransition } from "react";

import { backfillBaseballResultDivs } from "@/entities/baseball-result";
import type { DivBackfillResult } from "@/entities/baseball-result";
import { isAbortError } from "@/shared/lib";

import "./AdminPanel.css";

export function AdminPanel() {
  const [result, setResult] = useState<DivBackfillResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();

  const handleBackfill = () => {
    startTransition(async () => {
      try {
        const body = await backfillBaseballResultDivs();
        setResult(body);
        setError(null);
      } catch (err) {
        if (!isAbortError(err)) {
          setResult(null);
          setError(err instanceof Error ? err.message : "요청을 처리하지 못했습니다.");
        }
      }
    });
  };

  return (
    <div className="admin-panel">
      <h1>관리자</h1>

      <section className="admin-panel__section">
        <h2>승1패 배당 채우기</h2>
        <p className="admin-panel__description">
          야구 승1패 경기 중 홈/무/원정 배당이 비어 있는 데이터를 대상으로, 같은 경기의
          승패 배당을 이용해 세 배당을 계산하여 채웁니다.
        </p>

        <button
          type="button"
          className="admin-panel__button"
          onClick={handleBackfill}
          disabled={loading}
        >
          {loading ? "처리 중..." : "배당 채우기 실행"}
        </button>

        {error && <p className="admin-panel__error">{error}</p>}
        {result && (
          <p className="admin-panel__summary">
            대상 {result.target}건 / 갱신 {result.updated}건 / 건너뜀 {result.skipped}건
          </p>
        )}
      </section>
    </div>
  );
}
