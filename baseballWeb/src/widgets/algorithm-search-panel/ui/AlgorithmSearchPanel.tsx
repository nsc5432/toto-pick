import { useEffect, useRef, useState, useTransition } from "react";

import {
  ExperimentTable,
  fetchAlgorithmSpaces,
  fetchResearchGoal,
  runSearch,
} from "@/entities/research";
import type { AlgorithmSpace, ResearchGoal, SearchReport } from "@/entities/research";
import { formatRate, isAbortError } from "@/shared/lib";

import "./AlgorithmSearchPanel.css";

interface FormState {
  bgngYmd: string;
  endYmd: string;
  trainRatio: string;
  inputMoney: string;
  maxCandidatesPerAlgorithm: string;
  note: string;
}

const DEFAULT_FORM: FormState = {
  bgngYmd: "",
  endYmd: "",
  trainRatio: "0.7",
  inputMoney: "1000",
  maxCandidatesPerAlgorithm: "200",
  note: "",
};

interface AlgorithmSearchPanelProps {
  /** 탐색이 끝나 원장이 늘어났음을 알린다 — 리더보드를 다시 불러와야 한다. */
  onSearched?: () => void;
}

export function AlgorithmSearchPanel({ onSearched }: AlgorithmSearchPanelProps) {
  const [form, setForm] = useState<FormState>(DEFAULT_FORM);
  const [goal, setGoal] = useState<ResearchGoal | null>(null);
  const [spaces, setSpaces] = useState<AlgorithmSpace[]>([]);
  const [checkedCodes, setCheckedCodes] = useState<Set<string>>(new Set());
  const [report, setReport] = useState<SearchReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      fetchResearchGoal(controller.signal),
      fetchAlgorithmSpaces(controller.signal),
    ])
      .then(([loadedGoal, loadedSpaces]) => {
        setGoal(loadedGoal);
        setSpaces(loadedSpaces);
        setCheckedCodes(new Set(loadedSpaces.map((space) => space.code)));
      })
      .catch((err) => {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "탐색 설정을 불러오지 못했습니다.");
        }
      });
    return () => controller.abort();
  }, []);

  const handleChange = (key: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const toggleCode = (code: string) => {
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

  const handleSubmit = () => {
    if (!form.bgngYmd || !form.endYmd) {
      setError("시작일자와 종료일자를 입력하세요.");
      return;
    }
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    startTransition(async () => {
      try {
        const body = await runSearch(
          {
            bgngYmd: form.bgngYmd,
            endYmd: form.endYmd,
            trainRatio: Number(form.trainRatio),
            inputMoney: Number(form.inputMoney),
            maxCandidatesPerAlgorithm: Number(form.maxCandidatesPerAlgorithm),
            algorithmCodes: [...checkedCodes],
            note: form.note || undefined,
          },
          controller.signal,
        );
        setReport(body);
        setError(null);
        onSearched?.();
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "탐색에 실패했습니다.");
        }
      }
    });
  };

  const totalGrid = spaces
    .filter((space) => checkedCodes.has(space.code))
    .reduce((sum, space) => sum + space.gridSize, 0);

  return (
    <section className="algo-search">
      <h1>최적 알고리즘 탐색</h1>

      {goal && (
        <div className="algo-search__goal">
          <span className="algo-search__goal-label">목표치</span>
          <span>검증 수익률 ≥ {formatRate(goal.targetProfitRate)}</span>
          <span>조합 ≥ {goal.minSlipCount}건</span>
          <span>베팅일 ≥ {goal.minBettingDayCount}일</span>
          {goal.minHitRate !== null && <span>적중률 ≥ {formatRate(goal.minHitRate)}</span>}
          <span className="algo-search__goal-note">
            application.yaml 의 research.goal — 결과를 본 뒤 낮추지 않기 위해 화면에서 바꿀 수 없습니다.
          </span>
        </div>
      )}

      <div className="algo-search__form">
        <label>
          <span>시작일자(YMD)</span>
          <input value={form.bgngYmd} onChange={(e) => handleChange("bgngYmd", e.target.value)} />
        </label>
        <label>
          <span>종료일자(YMD)</span>
          <input value={form.endYmd} onChange={(e) => handleChange("endYmd", e.target.value)} />
        </label>
        <label>
          <span>학습 비율</span>
          <input
            value={form.trainRatio}
            onChange={(e) => handleChange("trainRatio", e.target.value)}
          />
        </label>
        <label>
          <span>투입금액</span>
          <input
            value={form.inputMoney}
            onChange={(e) => handleChange("inputMoney", e.target.value)}
          />
        </label>
        <label>
          <span>후보 예산</span>
          <input
            value={form.maxCandidatesPerAlgorithm}
            onChange={(e) => handleChange("maxCandidatesPerAlgorithm", e.target.value)}
          />
        </label>
        <label className="algo-search__note-field">
          <span>메모(가설)</span>
          <input value={form.note} onChange={(e) => handleChange("note", e.target.value)} />
        </label>
      </div>

      <div className="algo-search__algorithms">
        {spaces.map((space) => (
          <label key={space.code} className="algo-search__algorithm">
            <input
              type="checkbox"
              checked={checkedCodes.has(space.code)}
              onChange={() => toggleCode(space.code)}
            />
            <span className="algo-search__algorithm-name">{space.name}</span>
            <span className="algo-search__algorithm-meta">
              {space.tunable ? `노브 ${space.params.length} · 격자 ${space.gridSize}` : "고정"}
            </span>
          </label>
        ))}
      </div>

      <div className="algo-search__actions">
        <button type="button" onClick={handleSubmit} disabled={loading}>
          {loading ? "탐색 중…" : "탐색 실행"}
        </button>
        <span className="algo-search__budget">
          선택된 격자 합계 {totalGrid.toLocaleString()}점 — 예산을 넘으면 시드 고정 샘플링으로 축소됩니다.
        </span>
      </div>

      {error && <p className="algo-search__error">{error}</p>}

      {report && <SearchResult report={report} />}
    </section>
  );
}

function SearchResult({ report }: { report: SearchReport }) {
  return (
    <div className="algo-search__result">
      <div className="algo-search__windows">
        <span className="algo-search__window algo-search__window--train">
          학습 {report.trainWindow?.bgngYmd} ~ {report.trainWindow?.endYmd}
        </span>
        <span className="algo-search__window algo-search__window--validation">
          검증 {report.validationWindow?.bgngYmd} ~ {report.validationWindow?.endYmd}
        </span>
        <span className="algo-search__window-note">
          후보 {report.candidateCount.toLocaleString()}점 평가 · 검증 {report.validatedCount}건
        </span>
      </div>

      <div
        className={`algo-search__verdict${
          report.goalAchieved ? " algo-search__verdict--achieved" : ""
        }`}
      >
        {report.goalAchieved ? "목표 달성" : "목표 미달"}
      </div>

      <ul className="algo-search__diagnostics">
        {report.diagnostics.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>

      <h2>계열별 최고 후보</h2>
      <ExperimentTable
        experiments={report.bestPerAlgorithm}
        emptyText="평가된 후보가 없습니다."
      />
    </div>
  );
}
