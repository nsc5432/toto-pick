import { useRef, useState, useTransition } from "react";

import { simulatePicks } from "@/entities/pick-result";
import type { PickSimulationResult } from "@/entities/pick-result";

import "./PickSimulationPanel.css";

interface FormState {
  bgngYmd: string;
  endYmd: string;
  num: string;
  x: string;
  y: string;
  inputMoney: string;
  combinedN: string;
}

const DEFAULT_FORM: FormState = {
  bgngYmd: "260630",
  endYmd: "260630",
  num: "10",
  x: "2.5",
  y: "2.1",
  inputMoney: "10000",
  combinedN: "2",
};

interface FieldConfig {
  key: keyof FormState;
  label: string;
}

const FIELDS: FieldConfig[] = [
  { key: "bgngYmd", label: "시작일자(YMD)" },
  { key: "endYmd", label: "종료일자(YMD)" },
  { key: "num", label: "최근 경기수(NUM)" },
  { key: "x", label: "상위팀 최소배당(X)" },
  { key: "y", label: "하위팀 최대배당(Y)" },
  { key: "inputMoney", label: "투입금액" },
  { key: "combinedN", label: "조합수(N)" },
];

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}

function formatMoney(value: number): string {
  return value.toLocaleString();
}

function formatProfitRate(value: number | null): string {
  return value === null ? "-" : `${(value * 100).toFixed(1)}%`;
}

interface PickSimulationPanelProps {
  onCompleted?: () => void;
}

export function PickSimulationPanel({ onCompleted }: PickSimulationPanelProps) {
  const [form, setForm] = useState<FormState>(DEFAULT_FORM);
  const [result, setResult] = useState<PickSimulationResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();
  const controllerRef = useRef<AbortController | null>(null);

  const handleChange = (key: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleRun = () => {
    if (!/^\d{6}$/.test(form.bgngYmd) || !/^\d{6}$/.test(form.endYmd)) {
      setError("시작/종료일자는 6자리 숫자(YYMMDD)로 입력하세요.");
      return;
    }
    if (form.bgngYmd > form.endYmd) {
      setError("시작일자는 종료일자보다 클 수 없습니다.");
      return;
    }

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    startTransition(async () => {
      try {
        const body = await simulatePicks(
          {
            bgngYmd: form.bgngYmd,
            endYmd: form.endYmd,
            num: Number(form.num),
            x: Number(form.x),
            y: Number(form.y),
            inputMoney: Number(form.inputMoney),
            combinedN: Number(form.combinedN),
          },
          controller.signal,
        );
        setResult(body);
        setError(null);
        onCompleted?.();
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "시뮬레이션을 실행하지 못했습니다.");
        }
      }
    });
  };

  return (
    <section className="pick-simulation">
      <h1>PICK 시뮬레이션 (NSC)</h1>
      <p className="pick-simulation__description">
        기간 내 야구 승1패 경기를 대상으로, 최근 승률 상위/하위 5위 팀과 배당의 괴리를 이용해
        픽을 생성·정산하고 일자별 KPI를 보여줍니다.
      </p>

      <div className="pick-simulation__form">
        {FIELDS.map((field) => (
          <label key={field.key} className="pick-simulation__field">
            <span>{field.label}</span>
            <input
              type="text"
              value={form[field.key]}
              onChange={(e) => handleChange(field.key, e.target.value)}
            />
          </label>
        ))}
        <button
          type="button"
          className="pick-simulation__button"
          onClick={handleRun}
          disabled={loading}
        >
          {loading ? "실행 중..." : "시뮬레이션 실행"}
        </button>
      </div>

      {error && <p className="pick-simulation__error">{error}</p>}

      {result && (
        <>
          <p className="pick-simulation__summary">
            대상일 {result.summary.dayCount}일 / 조합 {result.summary.slipCount}건 / 적중{" "}
            {result.summary.hitCount}건 / 투입 {formatMoney(result.summary.inputTotal)} / 회수{" "}
            {formatMoney(result.summary.outputTotal)} / 수익률{" "}
            {formatProfitRate(result.summary.profitRate)}
          </p>

          <div className="pick-simulation__table-wrap">
            <table className="pick-simulation__table">
              <thead>
                <tr>
                  <th>일자</th>
                  <th>년도</th>
                  <th>회차</th>
                  <th>조합수</th>
                  <th>적중수</th>
                  <th>투입금액</th>
                  <th>회수금액</th>
                  <th>수익률</th>
                </tr>
              </thead>
              <tbody>
                {result.days.map((day) => (
                  <tr key={`${day.ymd}-${day.year}-${day.round}`}>
                    <td>{day.ymd}</td>
                    <td>{day.year}</td>
                    <td>{day.round}</td>
                    <td>{day.slipCount}</td>
                    <td>{day.hitCount}</td>
                    <td>{formatMoney(day.inputTotal)}</td>
                    <td>{formatMoney(day.outputTotal)}</td>
                    <td>{formatProfitRate(day.profitRate)}</td>
                  </tr>
                ))}
                {result.days.length === 0 && (
                  <tr>
                    <td colSpan={8} className="pick-simulation__empty">
                      기간 내 대상 경기가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
              {result.days.length > 0 && (
                <tfoot>
                  <tr>
                    <td colSpan={3}>합계</td>
                    <td>{result.summary.slipCount}</td>
                    <td>{result.summary.hitCount}</td>
                    <td>{formatMoney(result.summary.inputTotal)}</td>
                    <td>{formatMoney(result.summary.outputTotal)}</td>
                    <td>{formatProfitRate(result.summary.profitRate)}</td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        </>
      )}
    </section>
  );
}
