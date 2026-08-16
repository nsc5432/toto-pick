-- Run manually against MySQL schema `kbo`.
-- spring.jpa.hibernate.ddl-auto is `none` and this project has no migration tool (Flyway/Liquibase),
-- so this script is NOT executed automatically — apply it by hand.
--
-- Pre-game staging table: TEMP-like usage, cleared with DELETE ALL and re-populated with INSERT
-- each time it's used (handled directly via SQL, not by this application). Low row count, no
-- performance concerns. Not wired into any Java code in this iteration — reserved for a future
-- "generate picks for upcoming games" flow.

CREATE TABLE kbo.match_schedule (
    id           INT           NOT NULL AUTO_INCREMENT,
    year         INT           NOT NULL,
    round        INT           NOT NULL,
    TOURNAMENT   VARCHAR(20)   NOT NULL,
    YMD          VARCHAR(8)    NOT NULL,
    TM           VARCHAR(5)    NOT NULL,
    HOME         VARCHAR(20)   NOT NULL,
    AWAY         VARCHAR(20)   NOT NULL,
    GAME_TYPE    VARCHAR(20)   NOT NULL,
    COND         VARCHAR(10)   DEFAULT NULL,
    TOTAL_DIV    DOUBLE(5,2)   NOT NULL,
    PRIMARY KEY (id)
);
