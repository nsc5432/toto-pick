import { getJson, postJson } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

import type {
  PickAlgorithmInfo,
  PickKpiParams,
  PickKpiResult,
  PickResult,
  PickSimulationParams,
  PickSimulationResult,
  PickSlip,
  PickSlipParams,
} from "../model/types";

export function fetchPickResults(
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<PickResult>> {
  return getJson<PageResponse<PickResult>>(`/api/picks?page=${page}&size=${size}`, signal);
}

export function fetchAlgorithms(signal?: AbortSignal): Promise<PickAlgorithmInfo[]> {
  return getJson<PickAlgorithmInfo[]>("/api/picks/algorithms", signal);
}

export function simulatePicks(
  params: PickSimulationParams,
  signal?: AbortSignal,
): Promise<PickSimulationResult> {
  return postJson<PickSimulationResult>("/api/picks/simulate", params, signal);
}

export function fetchPickSlips(
  params: PickSlipParams,
  signal?: AbortSignal,
): Promise<PickSlip[]> {
  const query = new URLSearchParams({
    bgngYmd: params.bgngYmd,
    endYmd: params.endYmd,
  });
  if (params.algorithmCodes && params.algorithmCodes.length > 0) {
    query.set("algorithmCodes", params.algorithmCodes.join(","));
  }
  return getJson<PickSlip[]>(`/api/picks/slips?${query.toString()}`, signal);
}

export function fetchPickKpis(
  params: PickKpiParams,
  signal?: AbortSignal,
): Promise<PickKpiResult> {
  const query = new URLSearchParams({
    bgngYmd: params.bgngYmd,
    endYmd: params.endYmd,
    groupBy: params.groupBy,
  });
  if (params.algorithmCodes && params.algorithmCodes.length > 0) {
    query.set("algorithmCodes", params.algorithmCodes.join(","));
  }
  return getJson<PickKpiResult>(`/api/picks/kpis?${query.toString()}`, signal);
}
