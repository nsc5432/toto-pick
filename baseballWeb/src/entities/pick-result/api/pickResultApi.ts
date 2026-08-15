import { getJson } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

import type { PickResult } from "../model/types";

export function fetchPickResults(
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<PickResult>> {
  return getJson<PageResponse<PickResult>>(`/api/picks?page=${page}&size=${size}`, signal);
}
