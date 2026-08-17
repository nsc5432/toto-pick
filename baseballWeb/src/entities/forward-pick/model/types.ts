import type { BacktestMetrics, GoalCheck } from "@/entities/research";

/**
 * 경기 **전에** 기록된 픽 한 건.
 *
 * `createdAt`과 각 레그의 `backedOdds`가 이 기록의 전부다 — 경기보다 먼저 쓰였다는 증거이고,
 * 나중에 다시 계산하면 "그때 무엇을 보고 걸었는가"가 사라진다. 화면도 그래서 두 시각을 함께 보여준다.
 */
export interface ForwardPick {
  id: number;
  algorithmCode: string;
  algorithmName: string;
  paramSignature: string;
  userName: string;
  ymd: string;
  bucket: string;
  slipNo: number;
  inputMoney: number;
  combinedOdds: number | null;
  status: "PENDING" | "SETTLED" | "VOID";
  outputMoney: number | null;
  benchmarkOutputMoney: number | null;
  hit: boolean;
  createdAt: string;
  settledAt: string | null;
  legs: ForwardPickLeg[];
}

export interface ForwardPickLeg {
  legNo: number;
  tournament: string;
  ymd: string;
  tm: string;
  home: string;
  away: string;
  gameType: string;
  predictedTotalResult: string;
  backedOdds: number;
  overround: number;
  resultId: number | null;
  legResult: string | null;
  /** 미정산 레그는 null — 아직 맞힌 것도 틀린 것도 아니다. */
  legHit: boolean | null;
}

/** 정산 1회의 결과. `legExcessReturn`이 이 전체 작업이 존재하는 이유인 out-of-sample 수치다. */
export interface ForwardSettlement {
  algorithmCode: string;
  settledCount: number;
  stillPending: number;
  hitCount: number;
  legCount: number;
  legHitCount: number;
  stakedTotal: number;
  returnedTotal: number;
  legExcessReturn: number | null;
}

/** 픽을 백어웨이(무조건 원정)로 바꿔 같은 레그에 적용한 대조군. */
export interface ForwardBaseline {
  legCount: number;
  legHitCount: number;
  legHitRate: number;
  legReturnRate: number | null;
  legBenchmarkRate: number | null;
  legExcessReturn: number | null;
  legExcessTStat: number | null;
}

/**
 * 일일 루프가 실제로 돌았는지. `missedYmds`가 비어 있지 않으면 측정에 구멍이 있다는 뜻이고,
 * 사전 기록은 append-only이므로 그 날은 **영구적으로** 복구할 수 없다.
 */
export interface ForwardCoverage {
  firstYmd: string | null;
  lastYmd: string | null;
  playedDays: number;
  coveredDays: number;
  missedYmds: string[];
}

/**
 * 동결된 후보 하나의 전진 검증 성적. 지표와 판정 기준이 백테스트와 **같다** —
 * "백테스트는 X였는데 전진 검증은 Y"라는 비교가 이 화면의 존재 이유이므로 척도가 달라지면 안 된다.
 */
export interface ForwardReport {
  algorithmCode: string;
  algorithmName: string;
  userName: string;
  paramSignatures: string[];
  slipCount: number;
  settledSlipCount: number;
  pendingSlipCount: number;
  pendingYmds: string[];
  metrics: BacktestMetrics;
  goalAchieved: boolean;
  checks: GoalCheck[];
  failureSummary: string;
  awayBaseline: ForwardBaseline | null;
  excessOverAwayBaseline: number | null;
  backedSideCounts: Record<string, number>;
  coverage: ForwardCoverage;
}
