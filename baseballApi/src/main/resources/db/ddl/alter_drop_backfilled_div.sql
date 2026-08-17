-- 백필 배당 컬럼 제거 (설계 결정 D2의 종결).
--
-- HOME_DIV / DRAW_DIV / AWAY_DIV 는 TOTAL_DIV(실현된 면의 실측 배당)에서 오버라운드를 가정해 역산한
-- 추정치였다. 문제는 정확도가 아니라 **유추의 재료가 결과라는 것**이다:
--
--     결과가 "승" → HOME_DIV = TOTAL_DIV 그대로   |  DRAW_DIV, AWAY_DIV = 추정
--     결과가 "패" → AWAY_DIV = TOTAL_DIV 그대로   |  HOME_DIV, DRAW_DIV = 추정
--
-- 어느 컬럼이 정확한지를 결과가 정하므로, 선택 신호로 읽으면 백테스트가 답을 본다. 실제로 원정
-- 고내재확률 추종 전략에서 가짜 +35~55% ROI 를 만들었다 (statistical-model-design.md §7~§10).
--
-- §9 에서 발표 배당(PUB_*)을 확보해 D2 가 해제된 뒤로 이 컬럼들은 **선택 로직에서 아무도 읽지 않는다**.
-- 남겨두면 살아 있는 함정 둘만 남는다:
--   1. 알고리즘 작성자가 game.homeDiv() 에 손을 뻗을 수 있다 — 컴파일되고, 조용히 틀린다.
--   2. 발표 배당 적재 담당이 PUB_* 가 아니라 HOME_DIV 에 넣을 수 있다 — 에러 없이 전 경기가 스킵된다.
--
-- 감수하는 대가: 백필 대 발표값 비교를 다시 도출할 수 없다. 그 측정값(홈 6.1% / 원정 6.2% /
-- 무 11.9%, 판별력 역전)은 §9·§10 에 기록돼 있으므로 정보가 사라지지는 않는다.
--
-- 실행 순서 주의: **먼저 애플리케이션 코드를 배포한 뒤** 이 스크립트를 돌린다.
-- ddl-auto 가 none 이라 Hibernate 가 스키마를 검증하지 않으므로, 컬럼을 먼저 지우면 JPA 엔티티가
-- 여전히 매핑하고 있어 모든 SELECT 가 깨진다. 반대 순서는 무해하다(컬럼이 쓰이지 않고 남을 뿐).
--
-- 되돌릴 수 없으므로, 보존하고 싶다면 실행 전에 남길 것:
--   SELECT id, HOME_DIV, DRAW_DIV, AWAY_DIV FROM kbo.baseball_result;

ALTER TABLE kbo.baseball_result
    DROP COLUMN HOME_DIV,
    DROP COLUMN DRAW_DIV,
    DROP COLUMN AWAY_DIV;
