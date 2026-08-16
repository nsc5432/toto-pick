import { useEffect, useRef, useState, useTransition } from "react";

import { fetchAlgorithms, simulatePicks } from "@/entities/pick-result";
import type { PickAlgorithmInfo, PickSimulationResult } from "@/entities/pick-result";
import { formatMoney, formatRate, isAbortError } from "@/shared/lib";

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

export interface KpiQuery {
  bgngYmd: string;
  endYmd: string;
  algorithmCodes: string[];
}

interface PickSimulationPanelProps {
  /** refresh=true면 새 시뮬레이션이 수행되어 하위 목록/대시보드를 다시 불러와야 한다. */
  onQuery?: (query: KpiQuery, refresh: boolean) => void;
}

export function PickSimulationPanel({ onQuery }: PickSimulationPanelProps) {
  const [form, setForm] = useState<FormState>(DEFAULT_FORM);
  const [algorithms, setAlgorithms] = useState<PickAlgorithmInfo[]>([]);
  const [checkedCodes, setCheckedCodes] = useState<Set<string>>(new Set());
  const [result, setResult] = useState<PickSimulationResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchAlgorithms(controller.signal)
      .then((list) => {
        setAlgorithms(list);
        setCheckedCodes(new Set(list.map((a) => a.code)));
      })
      .catch((err) => {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "알고리즘 목록을 불러오지 못했습니다.");
        }
      });
    return () => controller.abort();
  }, []);

  const handleChange = (key: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const toggleAlgorithm = (code: string) => {
    setCheckedCodes((prev) => {
      const next = new Set(prev);
      if (next.has(code)) {
        next.delete(code);
      } else {
        next.add(code);
      }
      return next;
    });
  };

  const validate = (): KpiQuery | null => {
    if (!/^\d{6}$/.test(form.bgngYmd) || !/^\d{6}$/.test(form.endYmd)) {
      setError("시작/종료일자는 6자리 숫자(YYMMDD)로 입력하세요.");
      return null;
    }
    if (form.bgngYmd > form.endYmd) {
      setError("시작일자는 종료일자보다 클 수 없습니다.");
      return null;
    }
    if (checkedCodes.size === 0) {
      setError("알고리즘을 1개 이상 선택하세요.");
      return null;
    }
    setError(null);
    return {
      bgngYmd: form.bgngYmd,
      endYmd: form.endYmd,
      algorithmCodes: [...checkedCodes],
    };
  };

  const handleQueryOnly = () => {
    const query = validate();
    if (query) {
      onQuery?.(query, false);
    }
  };

  const handleRun = () => {
    const query = validate();
    if (!query) {
      return;
    }

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    startTransition(async () => {
      try {
        const body = await simulatePicks(
          {
            bgngYmd: query.bgngYmd,
            endYmd: query.endYmd,
            num: Number(form.num),
            x: Number(form.x),
            y: Number(form.y),
            inputMoney: Number(form.inputMoney),
            combinedN: Number(form.combinedN),
            algorithmCodes: query.algorithmCodes,
          },
          controller.signal,
        );
        setResult(body);
        setError(null);
        onQuery?.(query, true);
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "시뮬레이션을 실행하지 못했습니다.");
        }
      }
    });
  };

  return (
    <section className="pick-simulation">
      <h1>알고리즘 시뮬레이션 (NSC)</h1>
      <p className="pick-simulation__description">
        기간 내 야구 승1패 경기를 대상으로 선택한 알고리즘들의 픽을 생성·정산하고, 알고리즘별
        KPI(수익률·적중률)를 비교합니다.
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
      </div>

      <div className="pick-simulation__algorithms">
        <span className="pick-simulation__algorithms-label">알고리즘</span>
        {algorithms.map((algorithm) => (
          <label key={algorithm.code} className="pick-simulation__algorithm">
            <input
              type="checkbox"
              checked={checkedCodes.has(algorithm.code)}
              onChange={() => toggleAlgorithm(algorithm.code)}
            />
            <span>
              {algorithm.name} ({algorithm.code})
            </span>
          </label>
        ))}
        {algorithms.length === 0 && (
          <span className="pick-simulation__algorithms-empty">등록된 알고리즘이 없습니다.</span>
        )}
        <div className="pick-simulation__actions">
          <button
            type="button"
            className="pick-simulation__button"
            onClick={handleRun}
            disabled={loading}
          >
            {loading ? "실행 중..." : "시뮬레이션 실행"}
          </button>
          <button
            type="button"
            className="pick-simulation__button"
            onClick={handleQueryOnly}
            disabled={loading}
          >
            KPI 조회
          </button>
        </div>
      </div>

      {error && <p className="pick-simulation__error">{error}</p>}

      {result &&
        result.algorithms.map((run) => (
          <p key={run.algorithmCode} className="pick-simulation__summary">
            [{run.algorithmName}] 대상일 {run.dayCount}일 / 조합 {run.slipCount}건 / 적중{" "}
            {run.hitCount}건 (적중률 {formatRate(run.hitRate)}) / 투입{" "}
            {formatMoney(run.inputTotal)} / 회수 {formatMoney(run.outputTotal)} / 수익률{" "}
            {formatRate(run.profitRate)}
          </p>
        ))}
    </section>
  );
}
