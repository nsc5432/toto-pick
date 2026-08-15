import { getJson } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

import type { BaseballResult } from "../model/types";

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
