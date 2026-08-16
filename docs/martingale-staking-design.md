# 마틴게일 매수법 설계 — FAVORITE 2게임 조합 위 스테이킹 레이어

> 상태: **구현 완료.** `pick/domain/MartingaleStaking`(상태 머신) + `pick/domain/StakingAlgorithm`(계약) +
> `pick/domain/FavoriteMartingaleAlgorithm`(`FAVORITE_MARTINGALE`) + `PickBacktester.runStakedDay`(fold)로 구현되었고,
> `PickSimulationService`/`BacktestService` 양쪽 평가자에 연결되어 있다. 조합 내역 화면은 `GET /api/picks/slips` +
> 프론트 `widgets/pick-slip-list`. 규칙·수식·판정 기준은 아래 본문이 그대로 유효하다.
> 배경 문서: [statistical-model-design.md](statistical-model-design.md) — 특히 §1(파룰레이 수학), §12(시장 기대 대비 초과수익).

---

## 1. 개요와 동기

적중률이 가장 높은 조합은 **FAVORITE(배당 우세팀) 알고리즘 + 2게임 조합**(`combinedN=2`)이다. 여기에 **마틴게일 매수법**을 얹는다: 미적중하면 누적손실을 회수하고 목표수익까지 남도록 다음 배팅금을 증액하고, 적중하면 1단계로 리셋한다.

목표는 두 가지다.

1. 목표수익(`targetProfit`)을 실현하는 빈도를 높인다 — 대부분의 시퀀스는 몇 단계 안에 적중으로 끝난다.
2. 라운드(회차)당 손실을 **누적매수금액 한도 50,000원**으로 확실히 제한한다.

단, 마틴게일은 **기대값(EV)을 개선하지 못한다**. 손익의 분포만 바꾼다(자주 조금 벌고, 드물게 크게 잃는 구조). 이 사실과 그로 인한 백테스트 판정 기준은 §8에서 다룬다 — 이 절을 빼고 구현하면 반드시 오판한다.

## 2. 규칙 정의 (확정)

| # | 규칙 | 내용 |
|---|------|------|
| R1 | 배팅금 공식 | `배팅금 = (누적손실 + targetProfit) ÷ (조합배당 − 1)` 을 **50원 단위로 올림** |
| R2 | 하루 1슬립 | 같은 날 경기는 동시에 진행되어 앞 슬립 결과를 보고 다음 슬립을 걸 수 없다. 매일 FAVORITE `combinedN=2` 슬립 중 **조합배당이 가장 낮은(적중확률이 가장 높은) 슬립 1개**만 배팅한다. `FavoriteOddsSlipAlgorithm`은 후보를 배당 오름차순으로 정렬한 뒤 청킹하므로 **첫 번째 슬립**이 그것이다. |
| R3 | 한도 초과 | 라운드 내 누적매수금액(적중한 배팅 포함, 모든 배팅금의 합)이 50,000원을 넘을 수 없다. 다음 배팅금이 남은 예산을 초과하면 **남은 예산 전액으로 마지막 배팅**을 한 번 하고(목표수익 회수는 포기), 그 라운드의 배팅을 종료한다. |
| R4 | 적중 시 | 1단계로 리셋하고 **같은 라운드에서 계속** 배팅한다. 한 라운드에서 목표수익을 여러 번 달성할 수 있다. 누적매수 한도(R3)는 리셋과 무관하게 라운드 내내 계속 누적된다. |
| R5 | 라운드 경계 | 마틴게일 상태(단계, 누적손실, 사용 예산)는 **라운드가 바뀌면 초기화**된다. 라운드가 끝날 때 미회수 손실은 그 라운드 손실로 확정되며 다음 라운드로 이월하지 않는다(이월하면 R3의 라운드당 한도가 무의미해진다). |
| R6 | 슬립 없는 날 | 그날 FAVORITE 슬립이 없으면(경기 없음, `publishedOdds()` null 등) 배팅하지 않고 상태를 그대로 유지한다. |
| R7 | 방어 로직 | 조합배당 ≤ 1이면(이론상 파룰레이에서 불가능하지만) 그날은 건너뛴다. 0으로 나누기/음수 배팅금 방지. |

### 배팅금 반올림 주의

원 요청 예시의 1번 배팅 350원은 `1000 ÷ (4−1) = 333.3…`을 **50원 단위로 올림**한 값과 일치한다. 같은 규칙을 2번 배팅에 적용하면 정확값 `(350 + 1000) ÷ (3−1) = 675원`은 **700원**이 된다. 이 문서는 50원 단위 올림(`STAKE_ROUNDING_UNIT = 50`)을 표준으로 채택한다. 올림이므로 적중 시 수익은 항상 `targetProfit` **이상**이다.

> 참고: 원 요청 예시의 2번 배팅 1,100원은 표준 마틴게일 공식으로는 도출되지 않는 값이며, 사용자 확인을 거쳐 표준 공식(675원 → 올림 700원)으로 확정했다.

### 정정된 예시

`targetProfit = 1,000원`:

| 배팅 | 조합배당 | 누적손실(직전까지) | 계산 | 배팅금 | 적중 시 순수익 |
|------|---------|------------------|------|-------|--------------|
| 1번 | 4.0 | 0 | 1000 ÷ 3 = 333.4 | **350원** | 350×4 − 350 = 1,050원 |
| 2번 (1번 미적중 후) | 3.0 | 350 | (350+1000) ÷ 2 = 675 | **700원** | 700×3 − (350+700) = 1,050원 |

## 3. 상태 머신

라운드 키 `(year, round)`별 상태:

```
MartingaleState {
    step            // 현재 단계 (1부터)
    cumulativeLoss  // 현재 시퀀스에서 미회수된 손실 합 (원)
    usedBudget      // 이 라운드에서 지금까지 배팅한 금액 합 (원, 적중 배팅 포함)
}
```

하루의 처리 (라운드 내 일자를 ymd 오름차순으로 순차 진행):

```
슬립 없음(R6) 또는 조합배당 ≤ 1(R7)
    → 상태 유지, 다음 날로

stake = roundUp50((cumulativeLoss + targetProfit) / (조합배당 − 1))
remaining = 50,000 − usedBudget

remaining ≤ 0            → 이 라운드 배팅 종료 (이미 소진)
stake > remaining        → stake = remaining   // 마지막 배팅 (R3)

배팅 실행, usedBudget += stake

적중  → 순수익 = stake×조합배당 − (cumulativeLoss + stake) 실현
        step = 1, cumulativeLoss = 0            // 리셋 후 계속 (R4)
미적중 → cumulativeLoss += stake, step += 1
        (마지막 배팅이었다면 → 라운드 손실 확정, 배팅 종료)

라운드 변경 → 상태 전체 초기화 (R5)
```

**라운드 키 주의**: 시뮬레이션·백테스트의 처리 단위는 `DayKey(year, round, ymd)`이고, **한 ymd가 여러 `(year, round)`에 걸칠 수 있다**(둘 다 이 키로 그룹핑하며 `ymd → year → round` 순 정렬). 따라서 마틴게일 상태는 ymd가 아니라 `(year, round)`로 관리하고, 라운드 내부는 ymd 순으로 fold한다. 같은 ymd에 두 라운드가 걸치면 각 라운드가 각자의 상태로 그날 슬립 하나씩을 가져간다 — R2의 "하루 1슬립"은 정확히는 "라운드×일자당 1슬립"이다.

## 4. 워크드 예제 — 한 라운드 전체 (한도 도달 시나리오)

`targetProfit = 1,000원`, 한도 50,000원, 조합배당 3.0 고정 가정(FAVORITE 2게임 조합의 전형적 수준), 전부 미적중이 이어지는 최악 경로:

| 단계 | 누적손실(직전) | 계산 (÷2) | 배팅금 | 누적매수금액 |
|-----:|-------------:|----------:|-------:|-----------:|
| 1 | 0 | 500.0 | 500 | 500 |
| 2 | 500 | 750.0 | 750 | 1,250 |
| 3 | 1,250 | 1,125.0 | 1,150 | 2,400 |
| 4 | 2,400 | 1,700.0 | 1,700 | 4,100 |
| 5 | 4,100 | 2,550.0 | 2,550 | 6,650 |
| 6 | 6,650 | 3,825.0 | 3,850 | 10,500 |
| 7 | 10,500 | 5,750.0 | 5,750 | 16,250 |
| 8 | 16,250 | 8,625.0 | 8,650 | 24,900 |
| 9 | 24,900 | 12,950.0 | 12,950 | 37,850 |
| 10 | 37,850 | 19,425 → 필요 19,450 > 잔여 12,150 | **12,150 (잔액 전액, R3)** | **50,000** |

- 배팅금은 대략 `배당/(배당−1) = 1.5`배씩 기하급수로 커진다. **배당 3.0 기준 10단계째에 한도 도달.** 배당이 낮을수록(적중확률이 높을수록) 증가 속도가 빨라 더 일찍 도달한다.
- 10단계까지 미적중이면 라운드 손실 **−50,000원** 확정.
- 10단계(잔액 배팅)에서 적중해도 `12,150×3 − 50,000 = −13,550원` — 부분 회수일 뿐 라운드는 손실이다.
- 중간에 적중하면 그 시점까지의 손실을 회수하고 +1,000원 이상을 남긴 뒤 1단계부터 재시작(R4). 남은 예산은 `50,000 − 누적매수금액`.

## 5. 매개변수

### 스윕 대상 (ParamSpec)

| 이름 | 의미 | 범위 제안 | 기본값 |
|------|------|----------|-------|
| `targetProfit` | 적중 시 확보할 목표수익금 (원) | min 500, max 5000, step 500 | 1000 |

`MarketEdgeSlipAlgorithm`의 `edgeThreshold` 패턴을 따른다: 알고리즘에 `public static final String TARGET_PROFIT = "targetProfit"` 상수를 두고 `paramSpace()`에 `ParamSpec` 선언, 값은 `input.params().get(TARGET_PROFIT, 1000)`으로 읽는다(표준 4개 이름이 아니므로 `params()` 경유로만 전달된다).

`targetProfit`이 크면 1단계 배팅금부터 커져서 한도 도달 단계가 앞당겨진다(≈ 목표에 비례해 전 단계가 스케일됨). 예: 목표 5,000원이면 §4 표의 모든 금액이 약 5배 — 6단계 전후에 한도 도달. **스윕이 곧 '목표수익 vs 파산확률' 트레이드오프 탐색이다.**

### 고정 상수 (사용자 조건 — 스윕 금지)

| 이름 | 값 | 근거 |
|------|-----|------|
| `MAX_ROUND_STAKE` | 50,000원 | 라운드당 최대 누적매수금액 (요구 조건) |
| `STAKE_ROUNDING_UNIT` | 50원 | 배팅금 올림 단위 (§2 예시 기준) |
| `combinedN` | 2 고정 | "2게임 조합" 전제. `ParamSpec.fixed(AlgorithmParams.COMBINED_N, 2)`로 고정 선언 |

## 6. 아키텍처 통합 설계

### 6.1 왜 알고리즘 내부에 넣을 수 없는가

`PickAlgorithm.selectSlips(SlipSelectionInput)`은 **하루를 고립해서 보는 무상태 설계**다(전날 결과를 알 수 없음 — lookahead 차단 구조와 같은 이유로 의도된 것). 마틴게일은 전날 결과에 의존하는 **경로 의존** 로직이므로 알고리즘 안에 넣을 수 없고, 넣으려 하면 안 된다. 스테이킹은 알고리즘 바깥의 **평가자(evaluator) 레이어**가 fold하며 계산한다.

현재 정액 배팅이 하드코딩된 두 지점이 곧 삽입 지점이다:

- `pick/domain/PickBacktester.runDay(...)` — 하루 전체에 단일 스칼라 `inputMoney`
- `research/application/BacktestService.runDay` — `inputTotal = settings.inputMoney() × slips.size()`

### 6.2 신규 구성요소

1. **`pick/domain/MartingaleStaking`** (framework-free, §3 상태 머신 그대로)
   - 필드: `targetProfit`, 상수 `MAX_ROUND_STAKE`, `STAKE_ROUNDING_UNIT`, 라운드 키별 상태
   - `BigDecimal nextStake(RoundKey key, BigDecimal combinedOdds)` — 배팅금 반환, 예산 소진/방어 로직(R3, R7)이면 `null`(그날 배팅 없음)
   - `void settle(RoundKey key, BigDecimal stake, boolean hit)` — 상태 전이(R4)
   - **평가 실행(run)마다 새 인스턴스 생성** — 이래야 `AlgorithmSearchService`의 후보 병렬 스윕(`parallelStream`, 후보 간 독립)과 호환된다. 인스턴스를 공유하면 후보끼리 상태가 섞인다.

2. **신규 알고리즘 `FAVORITE_MARTINGALE`** (`pick/domain/FavoriteMartingaleAlgorithm`, `TunableAlgorithm`)
   - 선택 로직은 `FavoriteOddsSlipAlgorithm`에 **위임**하되 `combinedN=2` 고정, 반환 슬립을 **첫 1개로 축소**(R2 — 위임 결과가 배당 오름차순 첫 청크이므로 그것이 최저 조합배당 슬립)
   - `paramSpace()`: `targetProfit` range + `combinedN` fixed(§5)
   - `PickAlgorithmConfig`에 `@Bean` 등록 → `/api/picks/algorithms`, 시뮬레이션, 연구 스윕에 자동 노출
   - **코드 `FAVORITE_MARTINGALE`은 `pick_mstr.algorithm_code`와 실험 원장에 영속되므로 확정 후 변경 금지**

3. **스테이킹 인지 계약** — 알고리즘이 스테이킹 정책을 제공함을 평가자가 알 수 있는 최소 표면. 권장: `pick/domain`에 마커 인터페이스

   ```java
   public interface StakingAlgorithm extends PickAlgorithm {
       StakingSession newStakingSession(AlgorithmParams params); // = MartingaleStaking 인스턴스
   }
   ```

   평가자는 `algorithm instanceof StakingAlgorithm`이면 세션을 만들어 fold하고, 아니면 기존 정액 경로를 그대로 탄다. **`PickBacktester.runDay` 시그니처와 기존 알고리즘 5종은 무변경** — 이것이 이 방식을 (runDay 오버로드 대신) 권장하는 이유다.

### 6.3 수정 지점

| 파일 | 변경 |
|------|------|
| `research/application/BacktestService` | `evaluate`/`runDay`: `StakingAlgorithm`이면 일자 순 fold로 일별 stake를 산정해 `PickBacktester.runDay`에 전달, `inputTotal`을 실제 배팅금 합으로 계산. 슬립 0개 배팅인 날 처리(R3 소진, R6). **일자 순회가 ymd 오름차순 순차임을 보장**(후보 내부 병렬화 금지) |
| `pick/application/PickSimulationService` | 동일 fold를 적용해 슬립별 실제 배팅금을 `pick_mstr.input_money`에 저장. **스키마 변경 불필요** — `input_money DECIMAL(12,0)`은 이미 행마다 다른 값을 허용한다. 단계 번호까지 남기고 싶으면 `pick_mstr`에 컬럼 추가가 필요(선택사항, `alter_add_algorithm_code.sql` 전례를 따른 수동 DDL) |
| `research/application/BacktestSettings` | 변경 불필요가 기본. `inputMoney`는 정액 알고리즘용으로 유지되고 마틴게일은 자체 산정 — 혼동을 막기 위해 javadoc에 한 줄 명시 |
| `pick/infrastructure/algorithm/PickAlgorithmConfig` | `@Bean` 1개 추가 |

주의: `PickSimulationService`는 알고리즘을 `AlgorithmParams.empty()`로 실행한다(연구 백테스트만 `.withParams()`를 탄다). 따라서 **`targetProfit`의 기본값이 곧 운영 시뮬레이션 값**이다 — 스윕에서 승자가 나오면 기본값을 그 값으로 바꿔서 커밋하는 것까지가 한 사이클이다.

### 6.4 정산 일관성

정산은 기존 `PickSettlement.settle(details, results, inputMoney)` 그대로 사용한다 — `inputMoney`에 마틴게일 배팅금이 들어갈 뿐이다. 조합배당은 기존 규칙대로 `Π totalDiv`를 소수 둘째 자리 올림(CEILING)한 값이며, **`nextStake` 계산에 쓰는 조합배당도 반드시 같은 값**을 쓴다(선택 시점 배당은 `publishedOdds()` 기반이므로, 슬립의 조합배당 = 각 레그 backed side의 published 배당 곱을 같은 방식으로 올림). 산정과 정산이 다른 배당을 보면 "적중했는데 목표수익 미달"이 생긴다.

## 7. 평가 지표 — 기존 지표는 마틴게일을 볼 수 없다

기존 랭킹·게이트 스택은 전부 **배팅금액 불변**이다:

- `ObjectiveScore`는 레그 초과수익률(`LegTally`) 기반 — `LegTally`는 금액 자체를 모른다(레그당 1단위 고정).
- `BacktestMetrics.profitRate`/`excessReturn`/`hitRate`는 비율이라 정액 배팅에서는 스테이크와 무관.

즉 **마틴게일을 적용해도 현재 지표는 한 자리도 움직이지 않는다**(선택 슬립이 하루 1개로 줄어드는 효과 제외). 마틴게일의 효과는 금액 차원에만 존재하므로 신규 지표가 필요하다:

| 지표 | 정의 |
|------|------|
| 라운드 손익 분포 | 라운드별 `Σ(payout − stake)` — 평균, 중앙값, 최솟값(≥ −50,000 보장 확인) |
| 목표달성률 | 시퀀스(1단계 시작 → 적중 또는 소진) 중 적중으로 끝난 비율 |
| 한도소진률 | 라운드 중 누적매수 50,000원을 소진한 비율 — **핵심 리스크 지표** |
| 총투입 대비 수익률 | `Σprofit / Σstake` — 마틴게일은 회전(turnover)이 커서 정액과 직접 비교 불가, 반드시 별도 표기 |
| `maxDrawdown` | 이미 `BacktestMetrics`에 있고 금액 단위 — 마틴게일에서는 이것이 binding 제약이 되므로 `application.yaml`의 `research.goal.max-drawdown` 게이트(현재 주석 처리) 재활성화 검토 |

구현 위치: `DayOutcome`은 이미 `inputTotal`/`outputTotal`/`profit()`을 금액으로 들고 있으므로, 라운드 축 재집계는 `BacktestMetrics.from` 확장 또는 별도 `RoundOutcome` 집계로 가능하다. 운영 대시보드는 `GET /api/picks/kpis?groupBy=round`가 이미 라운드 축 KPI를 제공한다(`input_money`에 실제 배팅금이 저장되므로 추가 작업 없이 정확해진다).

## 8. 기대값 경고 — 반드시 읽고 판정할 것

수학적 사실: **스테이킹은 EV의 부호를 바꿀 수 없다.** 모든 배팅의 EV가 음수면 배팅금을 어떻게 배열해도 총 EV = Σ(개별 배팅 EV)는 음수다.

FAVORITE는 시장추종 계열이라 레그 기대수익 ≈ 시장 무기술 기대치 **−13.26%**([statistical-model-design.md](statistical-model-design.md) §12), 2게임 조합 슬립 기대치는 §1의 파룰레이 수학대로 `(1−0.1326)² − 1 ≈ **−24.8%**`다. 조합배당 3.0 기준 슬립 적중확률은 약 `1/(3.0 × 1.1529²) ≈ 25.1%`.

이 수치로 §4 시나리오의 시퀀스당 기대손익을 계산하면:

- 9단계 안에 적중할 확률 `1 − 0.749⁹ ≈ 92.5%` → 약 +1,000원
- 10단계 도달 확률 ≈ 7.5% → 잔액 배팅 적중 시 −13,550원 / 미적중 시 −50,000원
- **시퀀스당 EV ≈ 0.925×(+1,000) + 0.075×(0.251×(−13,550) + 0.749×(−50,000)) ≈ −2,100원**

즉 백테스트에서 "라운드의 90%+가 플러스"로 나오는 것은 개선이 아니라 **마틴게일의 정의 그 자체**다. 판정 기준을 미리 못 박는다:

1. **한도소진률을 기대치와 비교하라.** 무기술 가정의 이론 소진률(위 예시 ≈7.5%/시퀀스)보다 유의하게 낮아야 신호가 있는 것이다. 소진률이 이론값과 같으면 이 알고리즘의 성적은 −13.26% 마진을 그대로 먹는 정액 FAVORITE와 기대값이 동일하다.
2. **총 EV로만 판정하라**: `Σ라운드손익`이 0을 유의하게 넘는가. 목표달성률·적중률로 판정하면 안 된다.
3. 선택 규칙(하루 1슬립·최저 배당)이 레그 초과수익 자체를 바꿀 수는 있다 — 이것은 스테이킹 효과가 아니라 **선택 효과**이므로, 정액 배팅 버전(`FAVORITE_MARTINGALE`에 stake 고정)과 분리 측정해서 어느 쪽 효과인지 구분한다.
4. 기존 게이트(`min-excess-t-stat ≥ 2.0` 등)는 레그 레벨이라 그대로 유효하다 — 마틴게일이라고 면제되지 않는다.

이 알고리즘의 정직한 가치 제안은 "수익률 개선"이 아니라 **"레그 초과수익이 확인된 선택 규칙 위에서, 손실 상한이 보장된 현금흐름 형태로 바꾸는 것"**이다. 선택 규칙의 초과수익이 먼저다.

## 9. 구현 체크리스트 (다음 세션)

1. `pick/domain/MartingaleStaking` — 상태 머신 (§3). 단위 테스트: §2 정정 예시(350→700), §4 표 전체 재현, R3 잔액 배팅, R4 리셋 후 예산 지속, R5 라운드 초기화, R6/R7
2. `pick/domain/StakingAlgorithm` 인터페이스 (§6.2-3)
3. `pick/domain/FavoriteMartingaleAlgorithm` (`FAVORITE_MARTINGALE`) — 위임 + 1슬립 축소 + `paramSpace()` (§5). 조합배당 산정이 `PickSettlement`의 올림 규칙과 일치하는지 테스트 (§6.4)
4. `PickAlgorithmConfig`에 빈 등록 — 중복 코드 fail-fast 확인
5. `BacktestService` fold 적용 + 라운드 축 신규 지표 (§7)
6. `PickSimulationService` fold 적용 — 행별 `input_money` 저장 (스키마 변경 없음)
7. 스윕 실행: `POST /api/research/search`로 `targetProfit` 스윕 → §8 기준으로 판정 → 원장(`research/experiments.jsonl`) 기록 확인
8. (선택) 프론트 `/research`·KPI 대시보드에서 라운드 축 지표 노출 확인 (`groupBy=round`는 기존 기능)
