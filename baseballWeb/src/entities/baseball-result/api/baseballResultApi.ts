import { getJson, postJson } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

import type { BaseballResult, DivBackfillResult } from "../model/types";

export function fetchBaseballResults(
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<BaseballResult>> {
  return getJson<PageResponse<BaseballResult>>(
    `/api/baseball-results?page=${page}&size=${size}`,
    signal,
  );
}

export function backfillBaseballResultDivs(
  signal?: AbortSignal,
): Promise<DivBackfillResult> {
  return postJson<DivBackfillResult>(
    "/api/baseball-results/backfill-divs",
    undefined,
    signal,
  );
}
