# 작업 계획 — 백필 배당 컬럼 제거 → 전진 검증 적재

두 단계로 나눈다. **1단계가 2단계를 막지는 않지만**, 1단계를 먼저 하는 편이 낫다: 2단계에서 배당을
적재할 때 넣을 컬럼이 하나로 줄어들어 잘못 넣을 여지가 사라진다.

---

## 왜 지금 백필 컬럼을 지우는가

`HOME_DIV` / `DRAW_DIV` / `AWAY_DIV`는 `TOTAL_DIV`(실현된 면의 실측 배당)에서 오버라운드를 가정해
역산한 추정치다. **어느 컬럼이 정확한지를 결과가 정하므로**, 선택 신호로 읽으면 백테스트가 답을 본다
(설계 결정 D2, `statistical-model-design.md` §7~§10).

D2는 §10에서 이미 해제됐고 지금 이 컬럼들은 **선택 로직에서 아무도 읽지 않는다**. 남아 있는 이유는
"백필과 발표값의 차이가 D2 판정의 근거"였다는 역사적 명분뿐인데, 그 대가로 살아 있는 함정이 둘 있다:

1. 알고리즘 작성자가 `game.homeDiv()`에 손을 뻗을 수 있다 — 컴파일되고, 조용히 틀린다.
2. 발표배당 적재 담당이 `PUB_*`가 아니라 `HOME_DIV`에 넣을 수 있다 — 에러 없이 전 경기가 스킵된다.

### 받아들이는 대가 (명시)

지운 뒤에는 **백필 대 발표값 비교를 다시 도출할 수 없다.** 그 측정값(홈 상대오차 6.1% / 원정 6.2% /
무 11.9%, 판별력 역전 `avg(implied_home)` 승 0.376 · 1 0.380 · 패 0.387)은 §9·§10에 이미 기록돼 있어
**정보가 사라지는 것은 아니지만**, 재현은 불가능해진다. D2가 확정된 사안이므로 이 거래는 감수한다.

보험이 필요하면 지우기 전에 한 줄 덤프를 남긴다:

```sql
SELECT id, HOME_DIV, DRAW_DIV, AWAY_DIV FROM kbo.baseball_result;
-- → db/data/backfilled_odds_archive.tsv 로 저장 (코드가 읽지 않는 순수 기록물)
```

---

## 1단계 — `baseball_result`의 백필 배당 제거

### 순서가 중요하다: **코드 먼저, DDL 나중**

`spring.jpa.hibernate.ddl-auto`가 `none`이라 Hibernate가 스키마를 검증하지 않는다. 컬럼을 먼저 DROP하면
JPA 엔티티가 여전히 매핑하고 있어 **모든 SELECT가 깨진다**. 반대로 코드를 먼저 지우면 컬럼은 그냥
쓰이지 않고 남아 있을 뿐이라 무해하다.

### 1.1 통째로 삭제할 파일 (4개)

| 파일 | 비고 |
|---|---|
| `baseballresult/application/BaseballResultDivBackfillService.java` | 유추 계산 로직 본체 |
| `baseballresult/domain/DivUpdate.java` | 백필 전용 |
| `baseballresult/domain/DivBackfillResult.java` | 백필 전용 |
| `baseballresult/presentation/dto/DivBackfillResponse.java` | 백필 전용 |

`TWO_WAY_OVERROUND = TwoWayOddsEstimator.PUBLISHED_OVERROUND` 참조도 함께 사라진다 — 상수의 정본은
`TwoWayOddsEstimator`이므로 문제없다.

### 1.2 부분 수정

| 파일 | 지울 것 |
|---|---|
| `baseballresult/domain/BaseballResult.java` | 컴포넌트 `homeDiv`/`drawDiv`/`awayDiv` 3개 + 해당 javadoc. **20 → 17 컴포넌트** |
| `baseballresult/domain/BaseballResultRepository.java` | `int updateDivs(List<DivUpdate>)` |
| `baseballresult/infrastructure/persistence/BaseballResultJpaEntity.java` | `@Column` 3개(62~69행) + `applyDivs(...)`(84행) |
| `baseballresult/infrastructure/persistence/BaseballResultRepositoryImpl.java` | `updateDivs(...)` 구현(72~78행) + `DivUpdate` import |
| `baseballresult/presentation/BaseballResultController.java` | `POST /api/baseball-results/backfill-divs` + 주입된 서비스 필드 |
| `baseballresult/presentation/dto/BaseballResultResponse.java` | 필드 3개(20~22행) + `from()` 매핑(43~45행) |
| `baseballWeb/src/entities/baseball-result/model/types.ts` | `homeDiv`/`drawDiv`/`awayDiv` 3개 (그리드는 `totalDiv`만 렌더링하므로 UI 변경 없음) |

### 1.3 생성자 호출부 18곳

`BaseballResult`가 20 → 17 컴포넌트가 되므로 전부 고쳐야 한다. 지금은 이런 모양이다:

```java
..., totalResult, totalDiv,  null, null, null,  pubHome, pubDraw, pubAway)
//                           ^^^^^^^^^^^^^^^^^  이 셋을 지운다
```

| 위치 | 개수 |
|---|---|
| `baseballresult/infrastructure/persistence/BaseballResultRepositoryImpl.java` | 1 |
| `matchschedule/domain/MatchSchedule.java` (`asFixture()`) | 1 |
| 테스트 9개 파일 | 16 |

**함정**: 지워야 할 것은 `totalDiv`와 `pubHomeDiv` **사이의** 셋이다. 실수로 마지막 셋을 지우면
컴파일은 되지만 `publishedOdds()`가 null이 되어 알고리즘이 전 경기를 건너뛴다. 다행히 그때는
픽 단정문이 있는 테스트가 요란하게 깨지므로 조용히 통과하지는 않는다.

주석도 함께 손볼 것 — 다음 둘은 "백필 셋을 null로 둔다"고 설명하고 있어 의미가 없어진다:
`WinRateOddsSlipSelectorTest`(45행 근처), `MatchScheduleTest`(34행 근처).

### 1.4 javadoc / 문서에서 없어진 클래스를 가리키는 곳

| 파일 | 지금 표현 | 고칠 방향 |
|---|---|---|
| `baseballresult/domain/TwoWayOddsEstimator.java` | "1.14 that `BaseballResultDivBackfillService` had reverse-engineered" | 클래스명 대신 "과거 백필이 역산하던 1.14"로 |
| `pick/domain/MarketPairIndex.java` | "matching `BaseballResultDivBackfillService.matchKey`" | 키의 정본은 이제 `FixtureKey` |
| `pick/domain/PickSettlement.java` | "priced from the backfilled estimates (D2)" | "그 행의 `totalDiv`로 가격을 매기는 것(D2)"으로 |
| `pick/domain/MarketEdgeSlipAlgorithm.java` | "backfilled `*_DIV` columns must never feed…" | 컬럼이 사라졌다는 사실로 |
| `research/domain/TwoWayOddsCheck.java` | 백필 오차 수치 인용 | **그대로 둔다** — 문서를 인용하는 역사적 근거 |
| `CLAUDE.md` | "never use the `homeDiv`/`drawDiv`/`awayDiv` fields. Those three are backfilled estimates…" | **배당 출처가 하나뿐**이 되었다는 규칙으로 재작성 |
| `db/ddl/alter_add_published_div.sql` | "기존 컬럼은 지우지 않는다" | 이 계획으로 뒤집혔음을 주석으로 남긴다 |
| `docs/statistical-model-design.md` | §9·§10 | 역사는 고치지 않고, 컬럼이 제거됐고 재현 불가함을 **덧붙인다** |

### 1.5 DDL

새 파일 `db/ddl/alter_drop_backfilled_div.sql`:

```sql
ALTER TABLE kbo.baseball_result
    DROP COLUMN HOME_DIV,
    DROP COLUMN DRAW_DIV,
    DROP COLUMN AWAY_DIV;
```

`db/ddl/schema.sql`의 `baseball_result` 정의에서도 세 줄을 제거한다.

### 1.6 검증

1. `cd baseballApi && ./gradlew test` — 현재 119개 전부 통과 유지. **특히 픽 선택·정산 결과가
   변하지 않아야 한다**: 이 컬럼들은 선택에 쓰이지 않았으므로 수치가 한 자리도 움직이면 안 된다.
2. `cd baseballWeb && npm run build` — 타입 체크.
3. 코드 배포 후 DDL 실행. 순서를 지킬 것.
4. `GET /api/baseball-results?page=0&size=20` — 200이고 `homeDiv` 키가 사라졌는지.
5. `POST /api/research/search` (기존 알고리즘) — 원장 수치가 이전 실행과 동일한지 대조.

### 1.7 하지 않는 것

- **`PUB_*` 적재 스크립트는 만들지 않는다** (사용자가 적재 방법을 확보했으므로). `published_odds.tsv`도
  그대로 둔다.
- `TOTAL_DIV`는 건드리지 않는다 — 유일한 실측 배당이자 정산 지급의 근거다.

---

## 2단계 — 전진 검증 적재 (`match_schedule`)

### 선행 조건 (DDL 2개, 아직 미적용)

```
mysql kbo < baseballApi/src/main/resources/db/ddl/alter_match_schedule_published_div.sql
mysql kbo < baseballApi/src/main/resources/db/ddl/create_pick_forward.sql
```

현재 `POST /api/forward-picks/settle`이 500을 반환하는 이유가 `pick_forward` 부재다.
`GET /api/forward-picks/schedule-dates`는 이미 200 `[]`.

### 2.1 무엇을 적재하는가

경기일 **전에**, 한 경기마다 **두 행**:

| `GAME_TYPE` | `PUB_HOME_DIV` | `PUB_DRAW_DIV` | `PUB_AWAY_DIV` |
|---|---|---|---|
| `야구 승1패` | 필수 | 필수 | 필수 |
| `야구 승패` | 선택 | — (없음) | 선택 |

**`야구 승패` 행은 배당이 전부 NULL이어도 동작한다.** 그 행에서 필요한 것은 **정체성뿐**이고
(정산 대상 행을 찾는 용도), 가격 신호는 `승1패`의 발표배당을 `TwoWayOddsEstimator`로 변환해 얻는다.
`publishedOdds()`는 3면이 다 있어야 non-null이므로 `승패` 행에서는 어차피 null이며, 코드 경로상
문제없다 (`MarketPairIndex`와 `distinctFixtures`가 배당을 요구하지 않는다).

그래도 **`승패` 발표배당을 함께 수집해 두는 것을 권한다.** 지금은 아무도 읽지 않지만, 모이면
`TwoWayOddsEstimator`(모델 가정 하나)를 **실측으로 대체**할 수 있다. 현재 이 추정기의 근거는
사용자가 준 24경기뿐이다.

### 2.2 페어링이 전부다

두 행은 **`(year, round, TOURNAMENT, YMD, TM, HOME, AWAY)` 7개가 정확히 일치**해야 짝이 지어진다
(`FixtureKey`). 하나라도 어긋나면 그 경기는 승패 알고리즘에서 통째로 사라진다 — **에러 없이**.

가장 흔한 원인은 팀명 표기 흔들림이다. 과거 발표배당 수집에서도 "팀 약칭 → DB 팀명 부분수열 매핑"이
필요했다(§9). 적재 후 반드시 확인할 것:

```sql
SELECT COUNT(*) AS three_way,
       SUM(EXISTS (SELECT 1 FROM kbo.match_schedule t
                    WHERE t.GAME_TYPE = '야구 승패'
                      AND t.year = s.year AND t.round = s.round
                      AND t.TOURNAMENT = s.TOURNAMENT AND t.YMD = s.YMD
                      AND t.TM = s.TM AND t.HOME = s.HOME AND t.AWAY = s.AWAY)) AS paired
FROM kbo.match_schedule s
WHERE s.GAME_TYPE = '야구 승1패' AND s.YMD = '<적재한 날짜>';
```

`paired`가 `three_way`와 같아야 한다. 참고로 과거 데이터의 페어링률은 99.2%였다.

### 2.3 스모크 테스트

```bash
curl -s localhost:8080/api/forward-picks/schedule-dates          # 적재한 날짜가 보이는가
```

`scheduledGames`가 0이면 리그 필터(`KBO`/`NPB`/`MLB`)나 `GAME_TYPE` 문자열을 의심한다.

### 2.4 동결할 파라미터 — 되돌릴 수 없는 결정

전진 검증의 가치는 **파라미터가 데이터보다 먼저 고정됐다**는 데서만 나온다. 한 번 픽을 남기면
그 조합에 묶인다. 현재 근거상 최선:

```bash
curl -X POST localhost:8080/api/forward-picks/generate \
  -H 'Content-Type: application/json' -d \
  '{"ymd":"<적재한 날짜>","algorithmCode":"WIN_RATE_ODDS_2WAY",
    "params":{"combinedN":2,"num":10,"rankLimit":5,"x":1.4,"y":1.8,"oneRunHomeShare":0.6},
    "inputMoney":1000}'
```

근거: 검증 +0.1568 (t=1.87), 학습 +0.0626, **4/4 소구간 전부 양수**, ROI −0.0066(손익분기 근처),
레그 적중률 0.5635. 단 이 값들은 학습 구간에서 고른 뒤의 수치이므로 낙관 편향이 있다 — 동결하는
순간 그 사실이 확정된다.

**비교 대조군을 함께 돌리는 것을 권한다** (`FAVORITE_2WAY`, `WIN_RATE_ODDS`). 무기술 기준선이 같은
기간에 어떻게 나오는지 없으면, 전진 검증 결과가 좋아도 시장 상황 때문인지 알고리즘 때문인지
구분할 수 없다.

### 2.5 운영 루틴

| 시점 | 명령 |
|---|---|
| 경기 전 (매일) | `match_schedule` 적재 → `POST /forward-picks/generate` |
| 결과 적재 후 | `POST /forward-picks/settle?algorithmCode=…` |

`generate`는 **append-only**다 — 같은 날짜에 다시 돌리면 이미 기록된 슬립을 건너뛴다(덮어쓰지 않는다).
`settle`은 **멱등**이다 — 정산된 픽은 건드리지 않으므로 스케줄러에 걸어도 안전하다.

마틴게일 계열을 돌린다면 시퀀스 상태는 정산된 픽을 순서대로 재생해 복원되므로, **`settle`을 빠뜨리면
다음 배팅금이 틀린다.** 순서를 지킬 것.

### 2.6 목표 — 언제 판정이 나오는가

검증에 필요한 것은 약 **487다리**. 현재 볼륨(레그당 ~6.3다리/일) 기준 **2.5개월**이면 t = 2.0에
도달한다. 그때 처음으로 **오염되지 않은 판정**이 나온다.

그 전까지 나오는 중간 수치는 참고용이다 — 표본이 모이기 전에 "되네/안 되네"를 판단하는 것이
지금까지 위양성 네 번을 만든 바로 그 습관이다.
