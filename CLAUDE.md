# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo holds two independent, unlinked projects (no root build tool ties them together):

- `baseballApi/` — Spring Boot 4.1.0 backend (Java 17, Gradle Kotlin DSL)
- `baseballWeb/` — React 19 + TypeScript frontend (Vite 8)

## Commands

### baseballApi (run from `baseballApi/`)

- `./gradlew bootRun` — start the API on `http://localhost:8080`
- `./gradlew build` — compile, test, and package
- `./gradlew compileJava` — compile only (fast feedback while editing)
- `./gradlew test` — run all tests
- `./gradlew test --tests "com.toto.baseballApi.BaseballApplicationTests"` — run a single test class

### baseballWeb (run from `baseballWeb/`)

- `npm install` — install dependencies
- `npm run dev` — start the Vite dev server on `http://localhost:5173`
- `npm run build` — type-check (`tsc -b`) then production build
- `npm run lint` — run ESLint
- `npm run preview` — preview the production build

No test runner is configured in `baseballWeb` yet.

## Database

MySQL, schema **`kbo`** (not `toto`, despite the DB name suggested by the folder/project naming). Connection is configured in `baseballApi/src/main/resources/application.yaml`.

Six tables: `baseball_result` (finished games + odds), `match_schedule` (fixtures listed **before** they are played — the forward test's input, populated externally), `pick_mstr` (one betting slip; simulation slips carry `algorithm_code`), `pick_dtl` (slip legs), `pick_forward` + `pick_forward_leg` (picks recorded before their games, settled after).

DDL lives in `baseballApi/src/main/resources/db/ddl/`:
- `schema.sql` — full CREATEs for a fresh database
- `alter_add_algorithm_code.sql` — migration for databases that predate `pick_mstr.algorithm_code`
- `alter_add_published_div.sql` — adds `baseball_result.PUB_*`
- `alter_match_schedule_published_div.sql` — adds `match_schedule.PUB_*`, relaxes its `TOTAL_DIV`
- `create_pick_forward.sql` — the two forward-test tables

`spring.jpa.hibernate.ddl-auto` is `none` and there is no Flyway/Liquibase — apply DDL by hand with the mysql CLI.

## Pick algorithms — how to add one

Algorithms implement the domain port `pick/domain/PickAlgorithm` (`code()`, `name()`, `selectSlips(SlipSelectionInput)`). `code()` is the stable identity persisted to `pick_mstr.algorithm_code` and to the experiment ledger — never rename a code that has persisted rows.

- **Pure-logic algorithm** (only needs `SlipSelectionInput`): put the class in `pick/domain/` (framework-free) and register it with a `@Bean` method in `pick/infrastructure/algorithm/PickAlgorithmConfig`. Examples: `WinRateOddsSlipSelector` (`WIN_RATE_ODDS`), `FavoriteOddsSlipAlgorithm` (`FAVORITE`), `MarketEdgeSlipAlgorithm` (`MARKET_EDGE`).
- **Data-dependent algorithm** (needs repositories/services): put it in `pick/infrastructure/algorithm/` as a `@Component` implementing the domain port.

One bean with a unique `code()` is all it takes — the algorithm then automatically appears in `GET /api/picks/algorithms`, runs in `POST /api/picks/simulate`, and shows up in the frontend comparison dashboard. Duplicate codes fail fast at startup. Simulation KPIs are aggregated from persisted `pick_mstr` rows by `PickKpiService` (`GET /api/picks/kpis?bgngYmd=&endYmd=&groupBy=day|round&algorithmCodes=`).

Three rules that matter for anything new:

- **Read odds through `BaseballResult.publishedOdds()`, never the `homeDiv`/`drawDiv`/`awayDiv` fields.** Those three are backfilled *estimates* — only the realized side carries a measured price, so a rule reading them partly learns the outcome and the backtest is biased (`docs/statistical-model-design.md` 설계 결정 D2). `publishedOdds()` returns the `ThreeWayOdds` actually posted before the game, or `null` (skip the game — there is deliberately no fallback). De-vig through `ThreeWayOdds.impliedHome/Draw/Away()` rather than rolling your own, so every family measures the house margin the same way. `PickSettlement` pays at `totalDiv`, which is the published price of the side that won, so selection and settlement quote the same market.
- **Implement `TunableAlgorithm`, not bare `PickAlgorithm`.** Any threshold in the algorithm should be declared in `paramSpace()` as a `ParamSpec`. A constant baked into the code is never swept, so it is never validated. Declaring one of the four standard names (`num`, `x`, `y`, `combinedN`) feeds the swept value into the matching `SlipSelectionInput` component; any other name arrives via `input.params().get("name", fallback)` (see `MarketEdgeSlipAlgorithm`'s `edgeThreshold`).
- **Stake sizing never lives inside `selectSlips`.** Selection is stateless and slot-isolated by design; a path-dependent staking rule (e.g. martingale) implements `StakingAlgorithm` (`newStakingSession(params)`) and the evaluators (`PickSimulationService`, `BacktestService`) fold the session over the betting slots via `PickBacktester.runStakedSlot` — one session per run, slots in chronological order. A **slot** is one `(ymd, PickUniverse.combinationBucket(tournament))` pair: a 프로토 구매권 cannot mix time slots, so `{MLB}` (Korean morning) and `{KBO, NPB}` (evening) are separate, and the two slots of one day resolve in sequence. Because a slot spans 회차, build it through `PickUniverse.distinctFixtures` — the same game is routinely listed in two consecutive 회차 at different prices, and without collapsing them a "2게임 조합" can come out as the same game twice. Staking state is **unkeyed** — one sequence runs until a hit or until the 50,000원 budget is spent, and nothing else (a 회차 change least of all) resets it; keying it per `(year, round)` is what used to make a round change silently discard the running loss. Evaluators keep the flat `(year, round, ymd)` grouping for non-staking algorithms so their numbers stay comparable across the ledger. Example: `FavoriteMartingaleAlgorithm` (`FAVORITE_MARTINGALE`), design in `docs/martingale-staking-design.md`. `pick_mstr.input_money` stores the per-slip stake as-is (no schema change needed for variable stakes).
- **Two markets, and the choice of market matters more than most signals.** `야구 승1패` (3-way) settles `승` = home by 2+, `1` = **a one-run game** (not a draw — real ties are 16 rows in 2,614), `패` = away by 2+, so a favorite pick loses every one-run game and the market prices that slot at 22–28%. `야구 승패` (2-way) has no middle slot and a lower margin (overround 1.13629 vs 1.15). Same picks, same games: leg hit rate 41.4% → 56.4%, leg excess return about +6%p. Only the 3-way rows carry `PUB_*`, so a 2-way algorithm reads the signal from the paired 3-way row via `input.marketPairs()` (`MarketPairIndex`, keyed on `(year, round, tournament, ymd, tm, home, away)`), prices the leg through `TwoWayOddsEstimator.from(publishedOdds, oneRunHomeShare)`, and emits the **2-way row's id** so settlement resolves and pays there. Never price a 2-way leg off its own `totalDiv` — only the realized side is measured, which is 설계 결정 D2 exactly. Attach the derived price as the `PickSelection`'s `BackedPrice`; `PickSettlement` prefers it and falls back to `publishedOdds()`, so 3-way algorithms are untouched. Examples: `FavoriteTwoWaySlipAlgorithm` (`FAVORITE_2WAY`), `FavoriteTwoWayMartingaleAlgorithm` (`FAVORITE_2WAY_MARTINGALE`), `WinRateOddsTwoWaySlipSelector` (`WIN_RATE_ODDS_2WAY`); analysis in `docs/statistical-model-design.md` §13–§14. Validate the estimator with `GET /api/research/two-way-odds-check` before trusting anything computed on it. **An odds threshold does not carry across markets**: 2-way prices run 1.38–2.19 at the 5th–95th percentile against the 3-way 1.78–3.65, so a swept `x`/`y` range copied from a 3-way family sits outside the data and never binds — shift the range to the distribution, and compare against the price actually being taken. Form ranking is shared through `TeamRankSets.of(formIndex, ymd, num)`, so a 승패 variant differs from its 3-way sibling in exactly one place.
- **Read team form from `input.formIndex()`, never by walking `historyGames` yourself.** `TeamFormIndex.winPercent(team, input.ymd(), num)` only ever sees games before that date, which makes lookahead structurally impossible rather than a rule each caller has to remember. It is also built once per run instead of per day — a sweep of hundreds of candidates is only feasible because of this.

## Optimal-algorithm search pipeline (`research` feature)

The `research` feature turns "try an algorithm" into a repeatable loop: **분류/정의 → 구현 → 결과확인 → 목표치 판정**. It depends on `pick` (never the reverse) and writes nothing to the database.

- **Parameter space** — `pick/domain/{ParamSpec, ParamSpace, AlgorithmParams, TunableAlgorithm}`. `ParamSpace.candidates(budget, seed)` enumerates the exhaustive grid while it fits the budget and falls back to a seeded random sample beyond it.
- **Backtest** — `research/application/BacktestService` loads the range once (`BacktestData`: games grouped by day, history sorted, form index, history prefix lengths) and scores each candidate in memory. It settles through the same `pick/domain/PickBacktester` the real simulation uses, so search numbers and simulation numbers cannot diverge.
- **Train/validation split** — `BacktestSplit.byRatio` cuts the days chronologically (earlier days train, later days validate). `AlgorithmSearchService` selects parameters **only** on train, then re-scores just the top K on validation. Scoring every candidate on validation would leak the held-out window into selection.
- **Benchmark** — every leg's no-skill expected return is exactly `1/overround − 1` (de-vig identity: `p_side × odds_side = 1/O` whichever side is backed), measured here at −13.26%. `PickSettlement.marketExpectation`/`legTally` compute it per slip and per leg, so `BacktestMetrics` reports **excess return** — what the picks were worth beyond what any picks would have been. Raw ROI conflates "did it earn?" with "did it know something?", and in a 15.3%-margin market the first is nearly always no.
- **Ranking** — `ObjectiveScore`: validation **leg-level** excess return, behind a minimum-slip gate. Under-sampled candidates are kept and shown but sort below every qualified one, so a lucky 4-bet run can never win a search. Measured per leg rather than per slip because slip-level numbers swing wildly with leg grouping alone — the same games at 2 vs 3 legs once read −14.3% and +12.0%.
- **Goal** — `ExperimentGoal`, declared in `application.yaml` under `research.goal` before any search runs and deliberately **not** overridable per request. Evaluated against validation metrics; the train window enters only as a consistency check. Every bar is stated in standard errors (`min-excess-t-stat`, `max-train-validation-gap-t-stat`) rather than as an absolute number, because a fixed threshold is sample-size-blind — on 300 legs the noise alone is worth ±7%p. Three separate false positives in `docs/statistical-model-design.md` §10 and §12 all traced to that one flaw.
- **Ledger** — `research/experiments.jsonl` (append-only JSONL, `research/infrastructure/persistence/JsonlExperimentLedger`). Each entry carries params, both windows, both windows' metrics, the verdict, and the seed, so a run is re-runnable. `pick_mstr` cannot serve this: it is keyed on `algorithm_code` alone, so re-running at a different `x` overwrites the previous result.

Endpoints (`/api/research`): `GET /goal`, `GET /algorithms`, `POST /search`, `POST /backtest`, `GET /leaderboard?limit=`, `GET /experiments`, `GET /two-way-odds-check?bgngYmd=&endYmd=&oneRunHomeShare=` (validates the 3-way → 2-way price estimator against realized 승패 payouts). Frontend: `/research` (`pages/research`).

Committing a winner to `pick_mstr` stays a separate, deliberate `POST /api/picks/simulate` call — searching is exploration and must not litter the operational tables.

## Forward test (`matchschedule` feature + `pick/**/ForwardPick*`)

**Every number produced from `baseball_result` is measured on days that were available while the parameters were being chosen.** Four "goal achieved" verdicts have already turned out to be window effects (`docs/statistical-model-design.md` §10, §15), and no in-sample fix removes the problem — the validation window holds at most 628 legs, so even a +7%p edge tops out at t ≈ 1.58. A forward test is the only uncontaminated measurement available: pick games that have not been played, then settle them.

- **Input** is `match_schedule` (`matchschedule/domain/MatchSchedule`), not `baseball_result`. `asFixture()` shapes a listing as a `BaseballResult` so the existing algorithms select on it unchanged, with **outcome fields null** — those objects must never reach `PickSettlement`.
- **`ForwardPickService.generate`** mirrors the backtest exactly (same slot rule, same `MarketPairIndex`, all slips per slot; one slip for a `StakingAlgorithm`) and writes `pick_forward`. `params` is **required** — a forward test with unstated parameters is not testing the candidate that motivated it.
- **Append-only.** Re-running generation for a date skips slips already on record instead of replacing them; `createdAt` and every price recorded at bet time are the evidence, so nothing recomputes them later. Only settlement fields are written afterwards.
- **A pre-game pick cannot hold a `result_id`** — that row does not exist yet. It stores the fixture (`baseballresult/domain/FixtureKey`: year, round, tournament, ymd, tm, home, away — no `gameType`, so the two markets pair) and `ForwardPickSettlementService` resolves it once the game finishes.
- **Martingale state is replayed, never stored** — `ForwardPickService.replayStakingSession` folds settled forward picks in order. A stored counter drifts the moment a settlement is re-run, and R5 says nothing but a hit or budget exhaustion may reset a sequence.

Endpoints: `GET /api/forward-picks/schedule-dates`, `POST /api/forward-picks/generate`, `POST /api/forward-picks/settle?algorithmCode=&userName=`.

**`YMD` is 6-digit `YYMMDD`** (e.g. `260421`), despite the `VARCHAR(8)` column.

### Driving the loop with Claude

`.claude/skills/algo-search/` encodes one iteration of the loop: state check (ledger + API + goal), hypothesis classification against `references/taxonomy.md`, implementation, sweep, and verdict — plus the stop conditions and the anti-patterns (choosing parameters on validation numbers, reseeding until a run looks good, reporting train metrics as results). The taxonomy classifies signal families (market-following, form×odds, value/edge, schedule, odds-structure, combination) so a new hypothesis is a genuinely new family rather than a rename of an existing one.

## Statistical analysis via MCP

`.mcp.json` registers the `kbo-mysql` MCP server (`@benborla29/mcp-server-mysql`) so Claude can query the `kbo` schema directly — e.g. analyze `baseball_result` win rates/odds distributions to design and sanity-check new pick algorithms before implementing them.

- Requires the `KBO_DB_PASSWORD` environment variable (set once with `setx KBO_DB_PASSWORD "<password>"`; the password is not committed).
- The server is query-only: `ALLOW_INSERT/UPDATE/DELETE_OPERATION` are all `false`. Verify findings with read-only SQL, then codify the strategy as a `TunableAlgorithm` implementation and put it through the search pipeline. Checking for a signal in SQL first is much cheaper than implementing and sweeping one that isn't there.

## Backend architecture (baseballApi)

Packages are organized **feature-first, then by DDD layer**: `com.toto.baseballApi.<feature>/{domain, application, infrastructure/persistence, presentation}`. The `baseballresult` feature is the reference example for this pattern:

- `domain/` — framework-free model (a Java `record`) and a repository **port** interface (`BaseballResultRepository`). The only non-domain dependency allowed here is `org.springframework.data.domain.Page`/`Pageable`, accepted as a pragmatic exception so pagination doesn't need a hand-rolled abstraction.
- `application/` — use-case services (`BaseballResultService`) that orchestrate the domain repository port. No JPA or web concerns here.
- `infrastructure/persistence/` — the JPA adapter: `*JpaEntity` (the real `@Entity`, mapped with explicit `@Column(name = ...)` since the table's columns are a mix of lowercase and UPPERCASE), a Spring Data `*JpaRepository`, and a `*RepositoryImpl` that implements the domain port and maps JPA entities to domain records. These classes are package-private — nothing outside the package should reference them directly; only the domain port interface is a public contract.
- `presentation/` — `@RestController` plus a `dto/` response record with a `from(domain)` factory. Controllers never return domain records directly; they map through the response DTO.

When adding a new feature, follow this same four-layer package structure rather than a flat package of classes.

## Frontend architecture (baseballWeb)

Follows **Feature-Sliced Design (FSD)**, layered top to bottom: `app > pages > widgets > entities > shared`. Each slice exposes only its `index.ts` barrel as public API — cross-slice imports go through that barrel using the `@/` path alias (configured in both `vite.config.ts` and `tsconfig.app.json`), not deep relative paths into another slice's internals.

- `shared/` — framework-agnostic building blocks with no business logic, e.g. `shared/api` (a generic `getJson` fetch wrapper and `PageResponse<T>`).
- `entities/` — business entities: types plus their own data-fetching functions (e.g. `entities/baseball-result` exports the `BaseballResult` type and `fetchBaseballResults`).
- `widgets/` — composite UI blocks that combine entities/shared into something usable (e.g. `widgets/baseball-result-grid` renders the paginated table, `widgets/sidebar` renders the LNB navigation from a `config/menu.ts` list).
- `pages/` — route-level composition (e.g. `pages/baseball-result` just renders the grid widget).
- `app/` — routing (`react-router-dom`, defined in `src/app/App.tsx`) and top-level layout (LNB sidebar + content area).

When adding a new page/menu item: add the route in `app/App.tsx`, add an entry to `widgets/sidebar/config/menu.ts`, and build the feature as `entities` (data) + `widgets` (UI) + `pages` (composition), following the `baseball-result` slice as the template.

### Dev-time frontend/backend integration

`vite.config.ts` proxies `/api/*` to `http://localhost:8080`, so the frontend calls relative `/api/...` paths and avoids CORS entirely in development. There is no CORS configuration on the backend — it relies on this proxy.
