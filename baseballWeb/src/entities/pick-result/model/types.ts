export interface PickDetail {
  resultId: number;
  totalResult: string;
}

export interface PickResult {
  id: number;
  year: number;
  round: number;
  ymd: string | null;
  userName: string;
  algorithmCode: string | null;
  inputMoney: number;
  outputMoney: number | null;
  profitRate: number | null;
  details: PickDetail[];
}

export interface PickAlgorithmInfo {
  code: string;
  name: string;
}

export interface PickSimulationParams {
  bgngYmd: string;
  endYmd: string;
  num: number;
  x: number;
  y: number;
  inputMoney: number;
  combinedN: number;
  algorithmCodes?: string[];
}

export interface SimulationAlgorithmRun {
  algorithmCode: string;
  algorithmName: string;
  dayCount: number;
  slipCount: number;
  hitCount: number;
  hitRate: number | null;
  inputTotal: number;
  outputTotal: number;
  profitRate: number | null;
}

export interface PickSimulationResult {
  algorithms: SimulationAlgorithmRun[];
}

export interface PickSlipLeg {
  resultId: number;
  ymd: string | null;
  home: string | null;
  away: string | null;
  /** 픽한 결과: "승"(홈승) / "1"(무) / "패"(원정승) */
  predicted: string;
  /** 픽한 쪽의 발표 배당 (발표 배당이 없는 경기는 null) */
  backedOdds: number | null;
  actualResult: string | null;
  legHit: boolean;
}

export interface PickSlip {
  id: number;
  year: number;
  round: number;
  ymd: string | null;
  algorithmCode: string;
  inputMoney: number;
  outputMoney: number | null;
  hit: boolean;
  legs: PickSlipLeg[];
}

export interface PickSlipParams {
  bgngYmd: string;
  endYmd: string;
  algorithmCodes?: string[];
}

export type KpiGroupBy = "day" | "round";

export interface PickKpiParams {
  bgngYmd: string;
  endYmd: string;
  groupBy: KpiGroupBy;
  algorithmCodes?: string[];
}

export interface KpiSummary {
  slipCount: number;
  hitCount: number;
  hitRate: number | null;
  inputTotal: number;
  outputTotal: number;
  profitRate: number | null;
}

export interface PeriodKpi {
  periodKey: string;
  ymd: string | null;
  year: number;
  round: number;
  slipCount: number;
  hitCount: number;
  hitRate: number | null;
  inputTotal: number;
  outputTotal: number;
  profitRate: number | null;
}

export interface AlgorithmKpi {
  algorithmCode: string;
  algorithmName: string;
  summary: KpiSummary;
  periods: PeriodKpi[];
}

export interface PickKpiResult {
  groupBy: KpiGroupBy;
  algorithms: AlgorithmKpi[];
}
