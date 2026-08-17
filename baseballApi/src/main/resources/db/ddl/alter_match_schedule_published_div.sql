-- match_schedule 을 전진 검증(forward test)의 입력으로 쓰기 위한 정리.
--
-- 이 테이블은 경기 **전** 행만 담는다. 그래서 배당 컬럼 구성이 baseball_result 와 달라야 한다.
--
-- 1) PUB_* 추가 — 알고리즘이 배당을 읽는 유일한 통로는 BaseballResult.publishedOdds() 이고 그것은
--    PUB_* 만 읽는다 (설계 결정 D2, 폴백 없음). 따라서 발표배당은 여기 들어가야 하며, 다른 컬럼에
--    넣으면 알고리즘은 publishedOdds() == null 로 보고 **모든 경기를 조용히 건너뛴다**.
--
-- 2) TOTAL_DIV 삭제 — 경기 전에는 실현된 면이 없으므로 "실현된 면의 배당"이라는 값 자체가 존재할 수
--    없다. NOT NULL 이라 적재 시 더미를 넣어야 했고, 그 더미가 나중에 실측처럼 읽힐 여지가 있었다.
--
-- 3) HOME_DIV / DRAW_DIV / AWAY_DIV 삭제 — 읽는 코드가 하나도 없다. 남겨두면 함정으로만 기능한다:
--    적재 담당이 발표배당을 여기 넣어도 에러 없이 전 경기가 스킵될 뿐이다. 컬럼이 없으면 그 실수가
--    INSERT 에서 즉시 드러난다. baseball_result 쪽 동명 컬럼은 백필 추정치이므로 이름을 공유하는 것
--    자체도 오해를 부른다.
--
-- 주의: 이 테이블을 채우는 외부 적재 스크립트가 위 컬럼들을 명시하고 있다면 함께 고쳐야 한다.
-- 어차피 PUB_* 를 공급하도록 다시 써야 하므로, 테이블이 비어 있는 지금이 가장 싼 시점이다.

ALTER TABLE kbo.match_schedule
    ADD COLUMN PUB_HOME_DIV DOUBLE(5, 2) NULL COMMENT '발표 홈승 배당(원시)' AFTER COND,
    ADD COLUMN PUB_DRAW_DIV DOUBLE(5, 2) NULL COMMENT '발표 무(1점차) 배당(원시)' AFTER PUB_HOME_DIV,
    ADD COLUMN PUB_AWAY_DIV DOUBLE(5, 2) NULL COMMENT '발표 원정승 배당(원시)' AFTER PUB_DRAW_DIV;

ALTER TABLE kbo.match_schedule
    DROP COLUMN TOTAL_DIV,
    DROP COLUMN HOME_DIV,
    DROP COLUMN DRAW_DIV,
    DROP COLUMN AWAY_DIV;

-- 픽 생성은 (일자 × 게임유형)으로 조회한다.
ALTER TABLE kbo.match_schedule
    ADD KEY idx_match_schedule_ymd_type (YMD, GAME_TYPE);
