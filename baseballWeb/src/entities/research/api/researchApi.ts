import { getJson, postJson } from "@/shared/api";

import type {
  AlgorithmSpace,
  Experiment,
  ResearchGoal,
  SearchParams,
  SearchReport,
} from "../model/types";

export function fetchResearchGoal(signal?: AbortSignal): Promise<ResearchGoal> {
  return getJson<ResearchGoal>("/api/research/goal", signal);
}

export function fetchAlgorithmSpaces(signal?: AbortSignal): Promise<AlgorithmSpace[]> {
  return getJson<AlgorithmSpace[]>("/api/research/algorithms", signal);
}

/** 파라미터 스윕. 후보 수에 따라 수십 초가 걸릴 수 있어 호출부에서 진행 상태를 보여줘야 한다. */
export function runSearch(params: SearchParams, signal?: AbortSignal): Promise<SearchReport> {
  return postJson<SearchReport>("/api/research/search", params, signal);
}

/** 누적 원장 기준 알고리즘별 최고 기록. 이번 탐색 결과가 아니라 지금까지의 전체 기록이다. */
export function fetchLeaderboard(limit = 20, signal?: AbortSignal): Promise<Experiment[]> {
  return getJson<Experiment[]>(`/api/research/leaderboard?limit=${limit}`, signal);
}
