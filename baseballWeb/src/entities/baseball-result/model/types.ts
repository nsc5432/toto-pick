export interface BaseballResult {
  id: number;
  year: number;
  round: number;
  tournament: string;
  ymd: string;
  tm: string;
  home: string;
  away: string;
  gameType: string;
  cond: string | null;
  res1: number;
  res2: number | null;
  totalResult: string;
  totalDiv: number;
}
