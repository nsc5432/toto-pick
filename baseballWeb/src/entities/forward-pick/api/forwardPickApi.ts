import { getJson, postJson } from "@/shared/api";

import type { ForwardPick, ForwardReport, ForwardSettlement } from "../model/types";

/** `match_schedule`에 적재된 일자 — 적재가 어디까지 진행됐는지. */
export function fetchScheduleDates(signal?: AbortSignal): Promise<string[]> {
  return getJson<string[]>("/api/forward-picks/schedule-dates", signal);
}

/** 기록된 사전 픽 전체. 페이징하지 않는다 — 전진 검증의 값은 누적이지 마지막 페이지가 아니다. */
export function fetchForwardPicks(
  query: { algorithmCode?: string; userName?: string; status?: string } = {},
  signal?: AbortSignal,
): Promise<ForwardPick[]> {
  const search = new URLSearchParams();
  if (query.algorithmCode) search.set("algorithmCode", query.algorithmCode);
  if (query.userName) search.set("userName", query.userName);
  if (query.status) search.set("status", query.status);
  const qs = search.toString();
  return getJson<ForwardPick[]>(`/api/forward-picks${qs ? `?${qs}` : ""}`, signal);
}

/** 후보별 누적 성적과 목표 판정. 탐색과 **같은** `research.goal`로 판정된다. */
export function fetchForwardReports(signal?: AbortSignal): Promise<ForwardReport[]> {
  return getJson<ForwardReport[]>("/api/research/forward-report", signal);
}

/**
 * 경기가 끝난 뒤 정산. 여기서 쓰이는 것은 정산 결과뿐이고, 베팅 시점에 기록된 값은 건드리지 않는다.
 */
export function settleForwardPicks(
  algorithmCode: string,
  userName = "NSC",
  signal?: AbortSignal,
): Promise<ForwardSettlement> {
  const qs = new URLSearchParams({ algorithmCode, userName }).toString();
  return postJson<ForwardSettlement>(`/api/forward-picks/settle?${qs}`, undefined, signal);
}
