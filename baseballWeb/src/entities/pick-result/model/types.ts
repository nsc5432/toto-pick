export interface PickDetail {
  resultId: number;
  totalResult: string;
}

export interface PickResult {
  id: number;
  year: number;
  round: number;
  userName: string;
  inputMoney: number;
  outputMoney: number | null;
  profitRate: number | null;
  details: PickDetail[];
}
