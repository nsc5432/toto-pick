import { useCallback, useEffect, useState, useTransition } from "react";

import { fetchForwardReports, settleForwardPicks } from "@/entities/forward-pick";
import type { ForwardReport } from "@/entities/forward-pick";
import { formatMoney, formatRate, isAbortError } from "@/shared/lib";

import "./ForwardTestReport.css";

interface ForwardTestReportProps {
  refreshKey: number;
  onSettled: () => void;
}

function signedRate(value: number | null): string {
  if (value === null) {
    return "-";
  }
  const text = `${(value * 100).toFixed(2)}%p`;
  return value > 0 ? `+${text}` : text;
}

function toneOf(value: number | null): string {
  if (value === null) {
    return "";
  }
  return value > 0 ? "forward-report__value--good" : "forward-report__value--bad";
}

export function ForwardTestReport({ refreshKey, onSettled }: ForwardTestReportProps) {
  const [reports, setReports] = useState<ForwardReport[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();

  useEffect(() => {
    const controller = new AbortController();
    startTransition(async () => {
      try {
        setReports(await fetchForwardReports(controller.signal));
        setError(null);
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "전진 검증 성적을 불러오지 못했습니다.");
        }
      }
    });
    return () => controller.abort();
  }, [refreshKey]);

  const settle = useCallback(
    async (report: ForwardReport) => {
      try {
        const result = await settleForwardPicks(report.algorithmCode, report.userName);
        setNotice(
          `정산 ${result.settledCount}건 완료 · 미정산 ${result.stillPending}건 남음` +
            (result.settledCount === 0 ? " (경기 결과가 아직 적재되지 않았습니다)" : ""),
        );
        onSettled();
      } catch (err) {
        setError(err instanceof Error ? err.message : "정산에 실패했습니다.");
      }
    },
    [onSettled],
  );

  return (
    <section className="forward-report">
      <div className="forward-report__header">
        <h2>전진 검증 성적</h2>
        <span className="forward-report__note">
          이 화면의 수치만 in-sample이 아니다 — 파라미터를 동결한 시점에 존재하지 않던 경기로
          측정된다. 판정 기준은 탐색과 동일한 <code>research.goal</code>이며, 하루 약 10레그씩
          쌓이므로 표본 관문을 넘기까지 수 주가 걸린다.
        </span>
      </div>

      {error && <p className="forward-report__error">{error}</p>}
      {notice && <p className="forward-report__notice">{notice}</p>}

      {!error && reports.length === 0 && !loading && (
        <p className="forward-report__empty">
          기록된 사전 픽이 없습니다. <code>POST /api/forward-picks/generate</code>로 먼저 픽을
          기록하세요.
        </p>
      )}

      {reports.map((report) => {
        const metrics = report.metrics;
        const sides = Object.entries(report.backedSideCounts);
        const totalLegs = sides.reduce((sum, [, count]) => sum + count, 0);
        const dominant = sides.length > 0 ? Math.max(...sides.map(([, c]) => c)) : 0;
        const oneSided = totalLegs > 0 && dominant === totalLegs;

        return (
          <article className="forward-report__card" key={`${report.algorithmCode}-${report.userName}`}>
            <header className="forward-report__card-head">
              <div>
                <h3>
                  {report.algorithmName}{" "}
                  <span className="forward-report__code">{report.algorithmCode}</span>
                </h3>
                <p className="forward-report__params">
                  동결 파라미터: {report.paramSignatures.join(" / ") || "-"}
                </p>
              </div>
              <div className="forward-report__actions">
                <span
                  className={
                    report.goalAchieved
                      ? "forward-report__verdict forward-report__verdict--pass"
                      : "forward-report__verdict"
                  }
                >
                  {report.goalAchieved ? "목표 달성" : "판정 대기/미달"}
                </span>
                <button type="button" onClick={() => settle(report)}>
                  정산 실행
                </button>
              </div>
            </header>

            {report.coverage.missedYmds.length > 0 && (
              <p className="forward-report__gap">
                ⚠ 확인 필요 — 경기가 치러졌는데 픽이 없는 날 {report.coverage.missedYmds.length}일:{" "}
                {report.coverage.missedYmds.join(", ")}
                <br />
                루프가 안 돌았거나, 알고리즘이 그날 픽을 내지 않았거나입니다.{" "}
                <strong>기록만으로는 둘을 구분할 수 없습니다</strong> (사전 픽은 append-only이고
                0슬립 표시를 남기지 않음). 앞쪽이면 그 날은 되돌릴 수 없으니, 매일 적재와 픽 생성이
                돌았는지 확인하세요.
              </p>
            )}

            <div className="forward-report__grid">
              <div className="forward-report__metric">
                <span className="forward-report__label">기록 / 정산 / 대기</span>
                <span className="forward-report__value">
                  {report.slipCount} / {report.settledSlipCount} / {report.pendingSlipCount}
                </span>
              </div>
              <div className="forward-report__metric">
                <span className="forward-report__label">커버리지 (치러진 날 중 픽한 날)</span>
                <span className="forward-report__value">
                  {report.coverage.coveredDays} / {report.coverage.playedDays}
                </span>
              </div>
              <div className="forward-report__metric">
                <span className="forward-report__label">레그 (적중)</span>
                <span className="forward-report__value">
                  {metrics.legCount} ({metrics.legHitCount})
                </span>
              </div>
              <div className="forward-report__metric">
                <span className="forward-report__label">레그 적중률</span>
                <span className="forward-report__value">
                  {metrics.legCount === 0
                    ? "-"
                    : formatRate(metrics.legHitCount / metrics.legCount)}
                </span>
              </div>
              <div className="forward-report__metric forward-report__metric--primary">
                <span className="forward-report__label">레그 초과수익 — 판정 대상</span>
                <span
                  className={`forward-report__value ${toneOf(metrics.legExcessReturn)}`}
                >
                  {signedRate(metrics.legExcessReturn)}
                </span>
              </div>
              <div className="forward-report__metric forward-report__metric--primary">
                <span className="forward-report__label">유의성 t</span>
                <span className="forward-report__value">
                  {metrics.legExcessTStat === null ? "-" : metrics.legExcessTStat.toFixed(2)}
                </span>
              </div>
              <div className="forward-report__metric">
                <span className="forward-report__label">실현 / 무기술 레그 수익률</span>
                <span className="forward-report__value">
                  {formatRate(metrics.legReturnRate)} / {formatRate(metrics.legBenchmarkRate)}
                </span>
              </div>
              <div className="forward-report__metric">
                <span className="forward-report__label">투입 / 회수</span>
                <span className="forward-report__value">
                  {formatMoney(metrics.inputTotal, "-")} / {formatMoney(metrics.outputTotal, "-")}
                </span>
              </div>
            </div>

            <div className="forward-report__control">
              <h4>대조군 — 무조건 원정 베팅</h4>
              {report.awayBaseline === null ? (
                <p className="forward-report__empty">정산된 레그가 없어 아직 비교할 수 없습니다.</p>
              ) : (
                <p>
                  같은 레그에 원정만 걸었다면 초과수익{" "}
                  <strong>{signedRate(report.awayBaseline.legExcessReturn)}</strong> (적중{" "}
                  {report.awayBaseline.legHitCount}/{report.awayBaseline.legCount}). 픽이 그보다{" "}
                  <strong className={toneOf(report.excessOverAwayBaseline)}>
                    {signedRate(report.excessOverAwayBaseline)}
                  </strong>
                  .
                  {report.excessOverAwayBaseline !== null &&
                    Math.abs(report.excessOverAwayBaseline) < 0.0001 &&
                    " 차이가 0이면 이 후보는 사실상 원정 베팅과 같은 것을 하고 있다는 뜻이다."}
                </p>
              )}
              <p className="forward-report__sides">
                픽 방향: {sides.map(([side, count]) => `${side} ${count}`).join(" · ") || "-"}
                {oneSided && (
                  <strong className="forward-report__value--bad">
                    {" "}
                    — 전부 한쪽이다. "폼 × 배당"이 아니라 한쪽 진영 베팅이 되었는지 확인할 것.
                  </strong>
                )}
              </p>
            </div>

            <details className="forward-report__checks">
              <summary>목표 판정 상세 ({report.checks.filter((c) => c.passed).length}/{report.checks.length} 통과)</summary>
              <table>
                <thead>
                  <tr>
                    <th>항목</th>
                    <th>요구</th>
                    <th>실측</th>
                    <th>결과</th>
                  </tr>
                </thead>
                <tbody>
                  {report.checks.map((check) => (
                    <tr key={check.name}>
                      <td>{check.name}</td>
                      <td>{check.requirement}</td>
                      <td>{check.actual}</td>
                      <td className={check.passed ? "forward-report__value--good" : "forward-report__value--bad"}>
                        {check.passed ? "통과" : "미달"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          </article>
        );
      })}
    </section>
  );
}
