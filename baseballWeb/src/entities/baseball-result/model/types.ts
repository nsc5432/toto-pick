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
  /** Payout of the side that actually won — the only odds this row held before published prices. */
  totalDiv: number;
  /** Odds as posted before the game. The only odds an algorithm may select on. */
  pubHomeDiv: number | null;
  pubDrawDiv: number | null;
  pubAwayDiv: number | null;
}
