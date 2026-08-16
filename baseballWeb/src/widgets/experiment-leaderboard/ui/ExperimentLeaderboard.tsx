import { useEffect, useState, useTransition } from "react";

import { ExperimentTable, fetchLeaderboard } from "@/entities/research";
import type { Experiment } from "@/entities/research";
import { isAbortError } from "@/shared/lib";

import "./ExperimentLeaderboard.css";

interface ExperimentLeaderboardProps {
  /** 탐색이 끝날 때마다 증가시켜 누적 원장을 다시 읽는다. */
  refreshKey: number;
}

/**
 * 누적 원장 기준 알고리즘별 최고 기록. 방금 돌린 탐색이 아니라 **지금까지의 전체 이력**이라,
 * 이전 세션이 이미 찾아낸 최고 기록이 새 탐색 결과에 묻히지 않는다.
 */
export function ExperimentLeaderboard({ refreshKey }: ExperimentLeaderboardProps) {
  const [experiments, setExperiments] = useState<Experiment[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [, startTransition] = useTransition();

  useEffect(() => {
    const controller = new AbortController();

    startTransition(async () => {
      try {
        setExperiments(await fetchLeaderboard(20, controller.signal));
        setError(null);
      } catch (err) {
        if (!isAbortError(err)) {
          setError(err instanceof Error ? err.message : "리더보드를 불러오지 못했습니다.");
        }
      }
    });

    return () => controller.abort();
  }, [refreshKey]);

  const achieved = experiments.filter((experiment) => experiment.goalAchieved);

  return (
    <section className="leaderboard">
      <h2>누적 리더보드</h2>
      <p className="leaderboard__note">
        원장(experiments.jsonl)에 기록된 전체 실험 중 알고리즘별 최고 기록입니다.
        {achieved.length > 0 && ` 목표 달성 ${achieved.length}건.`}
      </p>

      {error && <p className="leaderboard__error">{error}</p>}

      <ExperimentTable
        experiments={experiments}
        emptyText="아직 기록된 실험이 없습니다. 위에서 탐색을 실행하세요."
      />
    </section>
  );
}
