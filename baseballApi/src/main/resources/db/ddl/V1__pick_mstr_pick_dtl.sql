-- Run manually against MySQL schema `kbo`.
-- spring.jpa.hibernate.ddl-auto is `none` and this project has no migration tool (Flyway/Liquibase),
-- so this script is NOT executed automatically — apply it by hand before using the pick feature.

CREATE TABLE kbo.pick_mstr (
    pick_mstr_id  INT           NOT NULL AUTO_INCREMENT,
    year          INT           NOT NULL,
    round         INT           NOT NULL,
    user_name     VARCHAR(20)   NOT NULL,
    input_money   DECIMAL(12,0) NOT NULL,
    output_money  DECIMAL(12,0) NULL,
    PRIMARY KEY (pick_mstr_id)
);

CREATE TABLE kbo.pick_dtl (
    pick_mstr_id  INT         NOT NULL,
    result_id     INT         NOT NULL,
    total_result  VARCHAR(5)  NOT NULL,
    PRIMARY KEY (pick_mstr_id, result_id),
    CONSTRAINT fk_pick_dtl_pick_mstr
        FOREIGN KEY (pick_mstr_id) REFERENCES kbo.pick_mstr (pick_mstr_id)
);
