-- Full schema for MySQL schema `kbo` — the single source of truth for all 6 tables.
-- spring.jpa.hibernate.ddl-auto is `none` and this project has no migration tool (Flyway/Liquibase),
-- so this script is NOT executed automatically — apply it by hand on a fresh database.
-- Column types (including deprecated DOUBLE(5,2) display widths) intentionally match the live DB verbatim.
--
-- This is the only DDL file. The incremental ALTERs that got the live database here (algorithm_code,
-- PUB_*, dropping the backfilled *_DIV trio, match_schedule, pick_forward) have all been applied and
-- were deleted rather than kept: a spent migration whose result is already folded in below can only
-- mislead, and there is exactly one database.

-- 경기 결과 (사후 확정 데이터)
--
-- 한 경기는 시장(GAME_TYPE)마다 한 행이다. 8개 시장이 적재돼 있고 — 야구 승1패 / 승패 / 핸디캡 /
-- 언더오버 / SUM 과 전반 승무패 / 전반 핸디캡 / 전반 언더오버 — 승1패·전반 승무패만 3면이라 PUB_*
-- 세 컬럼을 모두 채우고, 나머지는 홈/원정 두 개만 채우고 PUB_DRAW_DIV 를 NULL 로 둔다.
-- 핸디캡·언더오버는 같은 경기에 기준선(COND)별로 여러 행이 있을 수 있어 COND 가 유니크키에 포함된다.
CREATE TABLE kbo.baseball_result (
    id            INT           NOT NULL AUTO_INCREMENT COMMENT '아이디',
    year          INT           NOT NULL COMMENT '년도',
    round         INT           NOT NULL COMMENT '회차',
    TOURNAMENT    VARCHAR(20)   NOT NULL COMMENT '대회',
    YMD           VARCHAR(8)    NOT NULL COMMENT '경기일자',
    TM            VARCHAR(5)    NOT NULL COMMENT '경기시간',
    HOME          VARCHAR(20)   NOT NULL COMMENT '홈팀',
    AWAY          VARCHAR(20)   NOT NULL COMMENT '원정팀',
    GAME_TYPE     VARCHAR(20)   NOT NULL COMMENT '게임방식(시장)',
    COND          VARCHAR(10)   DEFAULT NULL COMMENT '조건 — 핸디캡/언더오버의 기준선',
    RES1          DOUBLE(5,2)   NOT NULL COMMENT '결과 1',
    RES2          DOUBLE(5,2)   DEFAULT NULL COMMENT '결과 2',
    TOTAL_RESULT  VARCHAR(5)    NOT NULL COMMENT '결과',
    TOTAL_DIV     DOUBLE(5,2)   NOT NULL COMMENT '실현된 면의 실측 배당 — 정산 지급의 근거',
    -- 알고리즘이 배당을 읽는 유일한 통로 (BaseballResult.publishedOdds() / publishedTwoWayOdds(),
    -- 설계 결정 D2). 여기서 역산한 HOME_DIV/DRAW_DIV/AWAY_DIV 가 있었으나, 어느 면이 정확한지를
    -- 결과가 정하는 구조라 제거했다 (statistical-model-design.md §7~§10).
    PUB_HOME_DIV  DOUBLE(5,2)   DEFAULT NULL COMMENT '발표 홈승 배당(원시)',
    PUB_DRAW_DIV  DOUBLE(5,2)   DEFAULT NULL COMMENT '발표 무(1점차) 배당(원시) — 2면 시장은 NULL',
    PUB_AWAY_DIV  DOUBLE(5,2)   DEFAULT NULL COMMENT '발표 원정승 배당(원시)',
    PRIMARY KEY (id),
    UNIQUE KEY BASEBALL_RESULT_IDX01 (year, round, TOURNAMENT, YMD, TM, HOME, AWAY, GAME_TYPE, COND)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 경기 전 스테이징 테이블 = 전진 검증의 입력. 외부 SQL로 적재 (Java는 읽기만 한다).
--
-- baseball_result 와 달리 결과 관련 컬럼이 없다 — 아직 치러지지 않은 경기이므로 실현된 면도,
-- 그 면의 배당도 존재하지 않는다. 배당은 PUB_* 하나뿐이고, 그것이 알고리즘이 읽는 유일한 통로다.
-- 다른 컬럼에 발표배당을 넣으면 에러 없이 모든 경기가 스킵되므로, 넣을 곳을 아예 하나로 두었다.
--
-- 두 시장을 함께 적재해야 페어링된다: 승패 알고리즘은 승1패 행으로 그날의 슬롯을 구성하고
-- (MarketPairIndex) 같은 경기의 승패 행에 베팅을 건다. 한쪽만 있으면 그 경기는 조용히 스킵된다.
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
    PUB_DRAW_DIV DOUBLE(5,2)  DEFAULT NULL COMMENT '발표 무(1점차) 배당(원시) — 2면 시장은 NULL',
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

-- 전진 검증(forward test)용 사전 픽 기록.
--
-- 왜 pick_mstr 을 쓰지 않는가: pick_dtl.result_id 는 baseball_result 를 가리키는데, 경기 전에는 그
-- 행이 존재하지 않는다. 더 근본적으로 pick_mstr 은 "정산된 픽"이고, 전진 검증에 필요한 것은
-- **경기보다 먼저 존재했다는 증거**다. 그래서 사전 픽은 여기에 append-only 로 남기고, 결과가 나오면
-- 정산해 채운다.
--
-- 이 테이블이 전진 검증의 유일한 근거이므로 다음 두 가지는 절대 사후에 바꾸지 않는다:
--   1. created_at — 픽이 경기 전에 만들어졌음을 증명한다.
--   2. input_money / combined_odds / backed_odds — 베팅 시점에 본 가격. 나중에 재계산하면
--      "그때 무엇을 보고 걸었는가"가 사라지고, 마틴게일은 그 값으로 배팅금을 정했으므로 특히 그렇다.
-- 사후에 채우는 것은 정산 결과(result_id, leg_result, output_money, settled_at, status)뿐이다.
CREATE TABLE kbo.pick_forward (
    pick_forward_id INT            NOT NULL AUTO_INCREMENT,
    algorithm_code  VARCHAR(30)    NOT NULL COMMENT '알고리즘 코드',
    param_signature VARCHAR(200)   NOT NULL COMMENT '동결된 파라미터 — 무엇을 검증 중인지의 정체',
    user_name       VARCHAR(20)    NOT NULL COMMENT '사용자',
    ymd             VARCHAR(8)     NOT NULL COMMENT '경기일자',
    bucket          VARCHAR(10)    NOT NULL COMMENT '조합버킷 (MLB / KBO_NPB)',
    -- 한 슬롯이 여러 슬립을 낳는다(백테스트와 동일). 스테이킹 알고리즘만 0번 하나로 제한된다.
    slip_no         INT            NOT NULL COMMENT '슬롯 안에서의 슬립 순번',
    input_money     DECIMAL(12, 0) NOT NULL COMMENT '베팅 시점에 정한 매수금액',
    combined_odds   DECIMAL(10, 2) DEFAULT NULL COMMENT '베팅 시점 조합배당 — 마틴게일이 사이징한 근거',
    status          VARCHAR(10)    NOT NULL COMMENT 'PENDING / SETTLED / VOID',
    output_money    DECIMAL(12, 0) DEFAULT NULL COMMENT '정산 후 적중금액',
    created_at      DATETIME       NOT NULL COMMENT '픽 생성 시각 — 경기 전임을 증명',
    settled_at      DATETIME       DEFAULT NULL COMMENT '정산 시각',
    PRIMARY KEY (pick_forward_id),
    KEY idx_pick_forward_algo_ymd (algorithm_code, ymd),
    KEY idx_pick_forward_status (status, ymd),
    -- 재실행해도 같은 슬립이 두 번 기록되지 않는다. 사전 기록의 가치는 "먼저 쓰였다"는 것이므로,
    -- 재실행은 덮어쓰기가 아니라 무시여야 한다.
    UNIQUE KEY uk_pick_forward_slip (algorithm_code, user_name, ymd, bucket, slip_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE kbo.pick_forward_leg (
    pick_forward_id INT           NOT NULL,
    leg_no          INT           NOT NULL COMMENT '레그 순번',
    -- 경기 전에는 baseball_result 행이 없으므로 경기 자체를 식별자로 들고 있는다.
    -- 정산 시 이 7개로 baseball_result 를 찾는다 (baseballresult/domain/FixtureKey).
    year            INT           NOT NULL,
    round           INT           NOT NULL,
    TOURNAMENT      VARCHAR(20)   NOT NULL,
    YMD             VARCHAR(8)    NOT NULL,
    TM              VARCHAR(5)    NOT NULL,
    HOME            VARCHAR(20)   NOT NULL,
    AWAY            VARCHAR(20)   NOT NULL,
    GAME_TYPE       VARCHAR(20)   NOT NULL COMMENT '정산 시장 (야구 승1패 / 야구 승패)',
    total_result    VARCHAR(5)    NOT NULL COMMENT '예측',
    backed_odds     DECIMAL(10, 2) NOT NULL COMMENT '베팅 시점 이 면의 가격',
    overround       DECIMAL(10, 6) NOT NULL COMMENT '그 시장의 마진 — 벤치마크 계산용',
    result_id       INT           DEFAULT NULL COMMENT '정산 시 매칭된 baseball_result.id',
    leg_result      VARCHAR(5)    DEFAULT NULL COMMENT '정산된 실제 결과',
    PRIMARY KEY (pick_forward_id, leg_no),
    CONSTRAINT fk_pick_forward_leg
        FOREIGN KEY (pick_forward_id) REFERENCES kbo.pick_forward (pick_forward_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
