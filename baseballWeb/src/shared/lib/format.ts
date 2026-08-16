/** 금액 표시: null은 미정산으로 표기한다. */
export function formatMoney(value: number | null, nullText = "미정산"): string {
  return value === null ? nullText : value.toLocaleString();
}

/** 비율(수익률/적중률) 표시: 백엔드가 주는 소수(fraction)를 %로 변환한다. */
export function formatRate(value: number | null): string {
  return value === null ? "-" : `${(value * 100).toFixed(1)}%`;
}
