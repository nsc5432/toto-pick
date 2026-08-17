/** 목표치 — 탐색을 돌리기 전에 선언된 기준선. 화면은 이 값을 하드코딩하지 않고 서버에서 받는다. */
export interface ResearchGoal {
  targetProfitRate: number;
  minSlipCount: number;
  minBettingDayCount: number;
  minHitRate: number | null;
  maxDrawdown: number | null;
}

export interface ParamSpec {
  name: string;
  min: number;
  max: number;
  step: number;
  defaultValue: number;
  cardinality: number;
}

export interface AlgorithmSpace {
  code: string;
  name: string;
  tunable: boolean;
  params: ParamSpec[];
  gridSize: number;
}

export interface BacktestWindow {
  label: string;
  bgngYmd: string;
  endYmd: string;
}

/**
 * 한 구간의 성적. **판정은 `legExcessReturn`으로 한다** — `profitRate`는 "돈을 벌었나"를 묻고,
 * 마진 15%대 시장에서 그 답은 거의 항상 '아니오'이며 전략이 무엇을 알았는지는 말해주지 않는다.
 * 레그 단위인 이유는 조합 단위 수치가 묶는 방식만 바꿔도 부호가 뒤집힐 만큼 노이즈가 크기 때문이다.
 */
export interface BacktestMetrics {
  dayCount: number;
  bettingDayCount: number;
  slipCount: number;
  hitCount: number;
  inputTotal: number;
  outputTotal: number;
  profitRate: number | null;
  hitRate: number | null;
  maxDrawdown: number;
  worstLosingStreak: number;
  segmentCount: number;
  profitableSegmentCount: number;
  benchmarkOutputTotal: number | null;
  excessReturn: number | null;
  legCount: number;
  legHitCount: number;
  legReturnRate: number | null;
  legBenchmarkRate: number | null;
  legExcessReturn: number | null;
  legExcessTStat: number | null;
}

export interface GoalCheck {
  name: string;
  requirement: string;
  actual: string;
  passed: boolean;
}

/**
 * 한 번의 실험. 학습/검증 지표가 **둘 다** 실려 온다 — 좋은 쪽만 골라 보여주면
 * 파라미터를 맞춘 구간의 수치를 성과로 읽게 되므로, 화면은 항상 둘을 같이 보여준다.
 */
export interface Experiment {
  id: string;
  createdAt: string;
  algorithmCode: string;
  algorithmName: string;
  params: Record<string, number>;
  paramSignature: string;
  trainWindow: BacktestWindow | null;
  validationWindow: BacktestWindow | null;
  trainMetrics: BacktestMetrics | null;
  validationMetrics: BacktestMetrics | null;
  score: number | null;
  qualified: boolean;
  goalAchieved: boolean;
  checks: GoalCheck[];
  failureSummary: string;
  inputMoney: string;
  seed: number;
  note: string | null;
}

export interface SearchParams {
  bgngYmd: string;
  endYmd: string;
  trainRatio?: number;
  inputMoney?: number;
  algorithmCodes?: string[];
  maxCandidatesPerAlgorithm?: number;
  validationTopK?: number;
  note?: string;
}

export interface SearchReport {
  trainWindow: BacktestWindow | null;
  validationWindow: BacktestWindow | null;
  goal: ResearchGoal;
  candidateCount: number;
  validatedCount: number;
  goalAchieved: boolean;
  bestPerAlgorithm: Experiment[];
  ranked: Experiment[];
  achieved: Experiment[];
  diagnostics: string[];
}
