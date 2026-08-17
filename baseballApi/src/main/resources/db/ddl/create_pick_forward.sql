-- 전진 검증(forward test)용 사전 픽 기록.
--
-- 왜 pick_mstr 을 쓰지 않는가: pick_dtl.result_id 는 baseball_result 를 가리키는데, 경기 전에는 그
-- 행이 존재하지 않는다. 더 근본적으로 pick_mstr 은 "정산된 픽"이고, 전진 검증에 필요한 것은
-- **경기보다 먼저 존재했다는 증거**다. 그래서 사전 픽은 여기에 append-only 로 남기고, 결과가 나오면
-- pick_mstr 로 승격시킨다 — 대시보드와 KPI 는 그대로 pick_mstr 만 보면 된다.
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
    -- 정산 시 이 7개로 baseball_result 를 찾는다 (BaseballResultDivBackfillService.matchKey 와 동일).
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
