-- Run manually against MySQL schema `kbo`.
-- spring.jpa.hibernate.ddl-auto is `none` and this project has no migration tool (Flyway/Liquibase),
-- so this script is NOT executed automatically — apply it by hand.
--
-- Per-outcome odds for GAME_TYPE = '야구 승1패' (home win / one-run margin / away win).
-- Nullable: only 승1패 rows get populated (via POST /api/baseball-results/backfill-divs),
-- and DRAW_DIV may stay NULL even then (estimated value out of plausible range).
-- match_schedule gets the same columns for external population — no Java code reads them yet.

ALTER TABLE kbo.baseball_result
    ADD COLUMN HOME_DIV DOUBLE(5,2) NULL AFTER TOTAL_DIV,
    ADD COLUMN DRAW_DIV DOUBLE(5,2) NULL AFTER HOME_DIV,
    ADD COLUMN AWAY_DIV DOUBLE(5,2) NULL AFTER DRAW_DIV;

ALTER TABLE kbo.match_schedule
    ADD COLUMN HOME_DIV DOUBLE(5,2) NULL AFTER TOTAL_DIV,
    ADD COLUMN DRAW_DIV DOUBLE(5,2) NULL AFTER HOME_DIV,
    ADD COLUMN AWAY_DIV DOUBLE(5,2) NULL AFTER DRAW_DIV;
