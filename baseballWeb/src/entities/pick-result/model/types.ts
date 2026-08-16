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
  inputMoney: number;
  outputMoney: number | null;
  profitRate: number | null;
  details: PickDetail[];
}

export interface PickSimulationParams {
  bgngYmd: string;
  endYmd: string;
  num: number;
  x: number;
  y: number;
  inputMoney: number;
  combinedN: number;
}

export interface PickSimulationSummary {
  dayCount: number;
  slipCount: number;
  hitCount: number;
  inputTotal: number;
  outputTotal: number;
  profitRate: number | null;
}

export interface PickSimulationDay {
  ymd: string;
  year: number;
  round: number;
  slipCount: number;
  hitCount: number;
  inputTotal: number;
  outputTotal: number;
  profitRate: number | null;
}

export interface PickSimulationResult {
  summary: PickSimulationSummary;
  days: PickSimulationDay[];
}
