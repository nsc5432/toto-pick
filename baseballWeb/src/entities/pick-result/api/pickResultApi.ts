import { getJson, postJson } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

import type { PickResult, PickSimulationParams, PickSimulationResult } from "../model/types";

export function fetchPickResults(
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<PickResult>> {
  return getJson<PageResponse<PickResult>>(`/api/picks?page=${page}&size=${size}`, signal);
}

export function simulatePicks(
  params: PickSimulationParams,
  signal?: AbortSignal,
): Promise<PickSimulationResult> {
  return postJson<PickSimulationResult>("/api/picks/simulate", params, signal);
}
