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

MySQL, schema **`kbo`** (not `toto`, despite the DB name suggested by the folder/project naming), table `baseball_result`. Connection is configured in `baseballApi/src/main/resources/application.yaml`.

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
