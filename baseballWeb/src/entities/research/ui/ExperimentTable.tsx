import { formatRate } from "@/shared/lib";

import type { Experiment } from "../model/types";

import "./ExperimentTable.css";

interface ExperimentTableProps {
  experiments: Experiment[];
  emptyText?: string;
}

/**
 * 실험 목록 표. 탐색 결과와 누적 리더보드가 같은 표를 쓰므로 엔티티에 둔다.
 *
 * 검증(held-out) 지표를 앞에, 학습 지표를 "참고용"으로 뒤에 두고 둘의 격차를 별도 열로 보여준다.
 * 학습 수익률만 높고 검증이 낮은 후보는 파라미터를 노이즈에 맞춘 것이고, 그 격차가 눈에 보이지
 * 않으면 화면이 그 후보를 성공처럼 읽히게 만든다.
 */
export function ExperimentTable({ experiments, emptyText }: ExperimentTableProps) {
  if (experiments.length === 0) {
    return <p className="experiment-table__empty">{emptyText ?? "기록이 없습니다."}</p>;
  }

  return (
    <div className="experiment-table__wrap">
      <table className="experiment-table">
        <thead>
          <tr>
            <th rowSpan={2}>알고리즘</th>
            <th rowSpan={2}>파라미터</th>
            <th colSpan={4}>검증 (held-out)</th>
            <th colSpan={2}>학습 (참고용)</th>
            <th rowSpan={2}>판정</th>
          </tr>
          <tr>
            <th className="experiment-table__sub">수익률</th>
            <th className="experiment-table__sub">적중률</th>
            <th className="experiment-table__sub">조합수</th>
            <th className="experiment-table__sub">최대낙폭</th>
            <th className="experiment-table__sub">수익률</th>
            <th className="experiment-table__sub">격차</th>
          </tr>
        </thead>
        <tbody>
          {experiments.map((experiment) => (
            <ExperimentRow key={experiment.id} experiment={experiment} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** 이 값을 넘는 학습-검증 격차는 과최적화 신호로 표시한다(백엔드 진단 임계값과 동일). */
const OVERFIT_GAP = 0.15;

function ExperimentRow({ experiment }: { experiment: Experiment }) {
  const validation = experiment.validationMetrics;
  const train = experiment.trainMetrics;
  const validationRate = validation?.profitRate ?? null;
  const trainRate = train?.profitRate ?? null;
  const gap = validationRate !== null && trainRate !== null ? trainRate - validationRate : null;

  const rateClass =
    validationRate === null
      ? undefined
      : validationRate > 0
        ? "experiment-table__cell--positive"
        : "experiment-table__cell--negative";

  return (
    <tr className={experiment.goalAchieved ? "experiment-table__row--achieved" : undefined}>
      <td className="experiment-table__name-cell">
        {experiment.algorithmName}
        <span className="experiment-table__code">{experiment.algorithmCode}</span>
      </td>
      <td className="experiment-table__params-cell">{experiment.paramSignature}</td>
      <td className={rateClass}>{formatRate(validationRate)}</td>
      <td>{formatRate(validation?.hitRate ?? null)}</td>
      <td className={experiment.qualified ? undefined : "experiment-table__cell--warn"}>
        {validation?.slipCount ?? 0}
        {!experiment.qualified && <span title="표본 부족 — 순위에서 실격"> ⚠</span>}
      </td>
      <td>{(validation?.maxDrawdown ?? 0).toLocaleString()}</td>
      <td className="experiment-table__train-cell">{formatRate(trainRate)}</td>
      <td className={gap !== null && gap > OVERFIT_GAP ? "experiment-table__cell--warn" : undefined}>
        {gap === null ? "-" : formatRate(gap)}
      </td>
      <td>
        {experiment.goalAchieved ? (
          <span className="experiment-table__badge">달성</span>
        ) : (
          <span className="experiment-table__failure" title={experiment.failureSummary}>
            {experiment.failureSummary || "미달"}
          </span>
        )}
      </td>
    </tr>
  );
}
