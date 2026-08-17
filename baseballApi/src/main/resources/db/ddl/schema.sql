-- Full schema for MySQL schema `kbo` — the single source of truth for all 6 tables.
-- spring.jpa.hibernate.ddl-auto is `none` and this project has no migration tool (Flyway/Liquibase),
-- so this script is NOT executed automatically — apply it by hand on a fresh database.
-- For an EXISTING database that predates `pick_mstr.algorithm_code`, run alter_add_algorithm_code.sql instead.
-- Column types (including deprecated DOUBLE(5,2) display widths) intentionally match the live DB verbatim.

-- 경기 결과 (사후 확정 데이터)
CREATE TABLE kbo.baseball_result (
    id            INT           NOT NULL AUTO_INCREMENT COMMENT '아이디',
    year          INT           NOT NULL COMMENT '년도',
    round         INT           NOT NULL COMMENT '회차',
    TOURNAMENT    VARCHAR(20)   NOT NULL COMMENT '대회',
    YMD           VARCHAR(8)    NOT NULL COMMENT '경기일자',
    TM            VARCHAR(5)    NOT NULL COMMENT '경기시간',
    HOME          VARCHAR(20)   NOT NULL COMMENT '홈팀',
    AWAY          VARCHAR(20)   NOT NULL COMMENT '원정팀',
    GAME_TYPE     VARCHAR(20)   NOT NULL COMMENT '게임방식',
    COND          VARCHAR(10)   DEFAULT NULL COMMENT '조건',
    RES1          DOUBLE(5,2)   NOT NULL COMMENT '결과 1',
    RES2          DOUBLE(5,2)   DEFAULT NULL COMMENT '결과 2',
    TOTAL_RESULT  VARCHAR(5)    NOT NULL COMMENT '결과',
    TOTAL_DIV     DOUBLE(5,2)   NOT NULL COMMENT '배당',
    HOME_DIV      DOUBLE(5,2)   DEFAULT NULL,
    DRAW_DIV      DOUBLE(5,2)   DEFAULT NULL,
    AWAY_DIV      DOUBLE(5,2)   DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY BASEBALL_RESULT_IDX01 (year, round, TOURNAMENT, YMD, TM, HOME, AWAY, GAME_TYPE, COND)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 경기 전 스테이징 테이블 = 전진 검증의 입력. 외부 SQL로 적재 (Java는 읽기만 한다).
--
-- baseball_result 와 달리 결과 관련 컬럼이 없다 — 아직 치러지지 않은 경기이므로 실현된 면도,
-- 그 면의 배당도 존재하지 않는다. 배당은 PUB_* 하나뿐이고, 그것이 알고리즘이 읽는 유일한 통로다
-- (BaseballResult.publishedOdds(), 설계 결정 D2). 다른 컬럼에 발표배당을 넣으면 에러 없이
-- 모든 경기가 스킵되므로, 넣을 곳을 아예 하나로 두었다.
CREATE TABLE kbo.match_schedule (
    id           INT          NOT NULL AUTO_INCREMENT,
    year         INT          NOT NULL,
    round        INT          NOT NULL,
    TOURNAMENT   VARCHAR(20)  NOT NULL,
    YMD          VARCHAR(8)   NOT NULL,
    TM           VARCHAR(5)   NOT NULL,
    HOME         VARCHAR(20)  NOT NULL,
    AWAY         VARCHAR(20)  NOT NULL,
    GAME_TYPE    VARCHAR(20)  NOT NULL COMMENT '야구 승1패 / 야구 승패 — 두 시장 모두 적재해야 페어링된다',
    COND         VARCHAR(10)  DEFAULT NULL,
    PUB_HOME_DIV DOUBLE(5,2)  DEFAULT NULL COMMENT '발표 홈승 배당(원시)',
    PUB_DRAW_DIV DOUBLE(5,2)  DEFAULT NULL COMMENT '발표 무(1점차) 배당(원시)',
    PUB_AWAY_DIV DOUBLE(5,2)  DEFAULT NULL COMMENT '발표 원정승 배당(원시)',
    PRIMARY KEY (id),
    KEY idx_match_schedule_ymd_type (YMD, GAME_TYPE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 픽 마스터: 조합 배팅 1건 (시뮬레이션 픽은 algorithm_code로 생성 알고리즘 식별)
CREATE TABLE kbo.pick_mstr (
    pick_mstr_id    INT            NOT NULL AUTO_INCREMENT,
    year            INT            NOT NULL,
    round           INT            NOT NULL,
    ymd             VARCHAR(8)     DEFAULT NULL,
    user_name       VARCHAR(20)    NOT NULL,
    algorithm_code  VARCHAR(30)    DEFAULT NULL,
    input_money     DECIMAL(12,0)  NOT NULL,
    output_money    DECIMAL(12,0)  DEFAULT NULL,
    PRIMARY KEY (pick_mstr_id),
    KEY idx_pick_mstr_user_ymd (user_name, ymd),
    KEY idx_pick_mstr_algo_ymd (algorithm_code, ymd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 픽 상세: 조합의 개별 경기 선택
CREATE TABLE kbo.pick_dtl (
    pick_mstr_id  INT         NOT NULL,
    result_id     INT         NOT NULL,
    total_result  VARCHAR(5)  NOT NULL,
    PRIMARY KEY (pick_mstr_id, result_id),
    CONSTRAINT fk_pick_dtl_pick_mstr
        FOREIGN KEY (pick_mstr_id) REFERENCES kbo.pick_mstr (pick_mstr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 전진 검증용 사전 픽. 전체 정의와 근거는 create_pick_forward.sql 참조 (여기서는 그 파일을 실행할 것).
-- source db/ddl/create_pick_forward.sql
