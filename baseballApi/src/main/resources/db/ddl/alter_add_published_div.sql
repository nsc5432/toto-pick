-- 원시(발표된) 3면 배당 컬럼 추가.
--
-- 기존 HOME_DIV / DRAW_DIV / AWAY_DIV 는 BaseballResultDivBackfillService 가 오버라운드 1.15 를
-- 가정하고 역산한 *추정치*다. 실측은 실현된 면의 TOTAL_DIV 하나뿐이므로, 그 컬럼들을 선택 신호로
-- 읽으면 "선택은 추정 배당 · 정산은 실측 배당"이 되어 백테스트가 방향 의존 편향을 얻는다
-- (docs/statistical-model-design.md 설계 결정 D2).
--
-- PUB_* 는 와이즈토토 프로토 승부식 회차 페이지에서 수집한 발표 배당이다. 추정이 아니라 게시된 값이며,
-- 실현된 면의 값이 TOTAL_DIV 와 일치하는지 전 행 교차검증을 통과했다.
-- 기존 컬럼은 지우지 않는다 — 백필과 발표값의 차이 자체가 D2 판정의 근거이므로 둘 다 남긴다.
--
-- [2026-08-17 정정] 위 문장은 뒤집혔다. 차이는 이미 측정돼 문서(§9·§10)에 남았고, 그 뒤로 백필
-- 컬럼은 선택 로직에서 아무도 읽지 않으면서 오적재·오참조 함정으로만 남아 있었다.
-- alter_drop_backfilled_div.sql 로 제거했다.
--
-- 적재: db/data/published_odds.tsv (id 기준)

ALTER TABLE baseball_result
    ADD COLUMN PUB_HOME_DIV DOUBLE(5, 2) NULL COMMENT '발표 홈승 배당(원시)' AFTER AWAY_DIV,
    ADD COLUMN PUB_DRAW_DIV DOUBLE(5, 2) NULL COMMENT '발표 무(1점차) 배당(원시)' AFTER PUB_HOME_DIV,
    ADD COLUMN PUB_AWAY_DIV DOUBLE(5, 2) NULL COMMENT '발표 원정승 배당(원시)' AFTER PUB_DRAW_DIV;
