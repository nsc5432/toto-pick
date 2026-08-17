---
name: algo-search
description: Run one iteration of the pick-algorithm optimization loop — classify a hypothesis, implement it as a PickAlgorithm, sweep its parameters against held-out days, and judge it against the declared goal. Use when asked to find/improve a betting algorithm, raise the profit rate, run a parameter search or backtest, add a new pick strategy, or continue the algorithm search.
---

# 최적 알고리즘 탐색 루프

한 번의 호출 = 한 번의 반복. 4단계를 **순서대로** 끝내고, 마지막에 다음 반복을 할지 멈출지 판정한다.

```
① 도출 방법 분류 · 가설 정의  →  ② 구현  →  ③ 결과 확인(탐색)  →  ④ 목표치 달성 판정
                     ↑                                                    │
                     └──────────── 미달 시 진단(diagnostics) 기반 재진입 ──┘
```

## 시작 전 — 상태 파악 (매번)

한 번도 건너뛰지 말 것. 이전 세션이 어디까지 갔는지 모르고 시작하면 같은 실패를 반복한다.

1. **원장 확인** — `baseballApi/research/experiments.jsonl` 을 읽는다. 없으면 첫 반복이다.
   이미 시도된 `algorithmCode` + `paramSignature` 조합은 **다시 탐색하지 않는다**.
2. **API 기동 확인**

   ```bash
   curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/research/goal
   ```

   200 이 아니면 백그라운드로 띄운다 (Bash tool, `run_in_background: true`):

   ```bash
   cd baseballApi && ./gradlew bootRun
   ```

   기동까지 30~60초. `/api/research/goal` 이 200 을 반환할 때까지 기다린 뒤 진행한다.
3. **목표치와 탐색 대상 확인**

   ```bash
   curl -s http://localhost:8080/api/research/goal
   curl -s http://localhost:8080/api/research/algorithms
   ```

4. **데이터 범위 확인** — `kbo-mysql` MCP 가 연결되어 있으면 읽기 전용 SQL 로, 아니면 사용자에게 묻는다.

   ```sql
   SELECT MIN(YMD), MAX(YMD), COUNT(*) FROM kbo.baseball_result WHERE GAME_TYPE = '야구 승1패';
   ```

---

## ① 도출 방법 분류 · 가설 정의

`references/taxonomy.md` 를 읽고 **어느 계열의 가설인지 먼저 분류한다**. 분류 없이 "일단 하나 만들어 보자"로
들어가면 이미 있는 알고리즘의 변형을 새 이름으로 다시 만들게 된다.

계열을 고르는 순서:

1. 원장에서 **아직 시도되지 않은 계열**을 우선한다.
2. 직전 탐색의 `diagnostics` 가 `"모든 계열이 검증 구간에서 손실"` 이라고 말하면 → **새 계열**을 추가한다.
   파라미터 재탐색은 효과가 없다.
3. `diagnostics` 가 `"표본 부족"` 이면 → 기존 계열의 **필터 완화**가 먼저다. 새 계열은 그 다음.
4. `diagnostics` 가 `"과최적화 의심"` 이면 → 격자를 좁히거나 기간을 늘려 **재검증**이 먼저다. 새 알고리즘 금지.

가설은 한 문장으로 적는다: **"어떤 관측 가능한 신호가, 시장 배당이 반영하지 못한 무엇을 담고 있는가."**
이 문장을 쓸 수 없으면 그 가설은 아직 구현할 준비가 안 된 것이다.

**MCP 로 먼저 검증한다** (`kbo-mysql`, 읽기 전용). 데이터에 신호의 흔적조차 없으면 구현하지 않는다 —
구현·탐색은 SQL 한 번보다 훨씬 비싸다.

---

## ② 구현

`CLAUDE.md` 의 알고리즘 추가 규약을 따른다. 그 위에 이 파이프라인이 요구하는 것:

- **`TunableAlgorithm` 을 구현한다** (`PickAlgorithm` 이 아니라). 임계값이 하나라도 있으면 그것은
  `paramSpace()` 에 선언되어야 한다. 코드에 박힌 상수는 탐색되지 않으므로 영원히 검증되지 않는다.
- 표준 노브 4개(`num`, `x`, `y`, `combinedN`)는 `ParamSpec` 에 선언하면 `SlipSelectionInput` 의 해당
  필드로 들어온다. 그 외 이름은 `input.params().get("이름", 기본값)` 으로 직접 읽는다
  (예시: `MarketEdgeSlipAlgorithm` 의 `edgeThreshold`).
- **팀 폼은 `input.formIndex()` 로 읽는다.** `winPercent(team, input.ymd(), num)` 은 그 날짜 이전 경기만
  본다 — 미래 참조(lookahead)를 구조적으로 막는 지점이다. `historyGames` 를 직접 순회해 폼을 재계산하지
  말 것. 느릴 뿐 아니라 날짜 경계를 직접 지켜야 한다.
- **격자 크기를 확인한다.** `ParamSpec` 4개를 넓게 잡으면 수천 점이 되고, 예산(200)을 넘으면 랜덤
  샘플링으로 떨어져 탐색이 성겨진다. 노브당 5~10 값, 총 격자 수백 점을 목표로 한다.
- `PickAlgorithmConfig` 에 `@Bean` 등록. `code()` 는 **한 번 원장에 기록되면 절대 바꾸지 않는다** —
  바꾸는 순간 과거 실험과의 연결이 끊긴다.
- 도메인 테스트를 함께 쓴다. 최소한: 신호가 잡히는 경우, 안 잡히는 경우, 파라미터 경계값.

```bash
cd baseballApi && ./gradlew test --tests "com.toto.baseballApi.pick.*"
```

테스트가 통과하면 API 를 재기동해야 새 빈이 잡힌다 (devtools 가 자동 재시작하지만, 200 응답으로 확인할 것).

```bash
curl -s http://localhost:8080/api/research/algorithms
```

새 `code` 가 목록에 보이지 않으면 재기동이 안 된 것이다. 여기서 멈추고 해결한다.

---

## ③ 결과 확인 — 탐색 실행

전체 알고리즘을 같은 구간·같은 조건으로 돌린다. 새 알고리즘만 돌리면 기존 대비 개선인지 알 수 없다.

**`YMD` 는 6자리 `YYMMDD` 다** (`260421`, 8자리 아님). 현재 적재 범위는 SQL 로 확인한다.

한글이 든 `note` 는 셸 인자로 넘기면 cp949 로 인코딩되어 400(Invalid UTF-8) 이 난다.
**본문을 UTF-8 파일로 쓴 뒤 `--data-binary @파일`** 로 보낼 것.

```bash
curl -s -X POST http://localhost:8080/api/research/search \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data-binary @body.json -o report.json
```

```json
{ "bgngYmd": "260421", "endYmd": "260815", "note": "가설: <한 문장>" }
```

`bgngYmd`/`endYmd` 외에는 전부 선택이다 — 비워 두면 `application.yaml` 의 예산을 쓴다.
`trainRatio`, `maxCandidatesPerAlgorithm`, `validationTopK`, `algorithmCodes`, `inputMoney` 로만 조정한다.

**목표치는 요청으로 못 바꾼다.** 의도적인 설계다. 결과를 본 뒤 기준을 낮추면 판정이 무의미해진다.
목표 자체를 조정해야 한다고 판단되면 코드가 아니라 **사용자에게 근거와 함께 제안**한다.

응답에서 볼 것:

| 필드 | 의미 |
|---|---|
| `bestPerAlgorithm` | 계열별 최고 후보. 계열 간 비교는 **여기**로 한다 |
| `validationMetrics` | **유일하게 인용해도 되는 수치.** 검증(held-out) 구간 결과 |
| `trainMetrics` | 파라미터를 고른 구간. 검증과 비교하는 용도로만 본다 |
| `diagnostics` | 다음에 뭘 할지. ①의 분기 조건이 여기서 나온다 |
| `goalAchieved` | ④의 답 |

`trainMetrics.profitRate` 만 좋고 `validationMetrics.profitRate` 가 나쁘면 **과최적화다. 성공으로 보고하지 않는다.**

---

## ④ 목표치 달성 판정

`goalAchieved` 가 판정이다. 직접 다시 계산하지 않는다.

### 달성했을 때 — **먼저 감사한다 (건너뛰지 말 것)**

`goalAchieved: true` 는 보고할 결과가 아니라 **검증해야 할 주장**이다. 지금까지 이 프로젝트에서 나온
"달성" 은 대부분 위양성이었고(`docs/statistical-model-design.md` §10 · §15), 매번 같은 모양이었다:
검증 창에만 몰린 수익, 학습 대비 몇 배로 뛴 초과수익, 이웃 없이 홀로 튀는 파라미터.

우승 파라미터를 **달력 소구간별로 다시 측정**한다. 창 하나를 통째로 재는 것과 넷으로 쪼개 재는 것은
다른 질문이고, 위양성을 두 번 다 잡아낸 것은 후자다:

```bash
for w in 260421:260531 260601:260630 260701:260731 260801:260815; do
  curl -s -X POST http://localhost:8080/api/research/backtest \
    -H 'Content-Type: application/json' -d \
    "{\"algorithmCode\":\"<코드>\",\"bgngYmd\":\"${w%%:*}\",\"endYmd\":\"${w##*:}\",
      \"trainRatio\":0.01,\"params\":{<우승 params 그대로>},\"note\":\"감사\"}"
done
```

**하나라도 해당하면 위양성으로 판정하고 미달로 처리한다** — `simulate` 하지 않는다:

- 소구간 중 **음수가 하나라도** 있다 (진짜 엣지는 구간마다 크기가 달라도 부호는 유지된다)
- 초과수익이 사실상 **검증 창 구간에만** 몰려 있다 (= 창 효과, §10)
- **학습 대비 검증이 2배 이상** 뛴다 (게이트를 통과하더라도 — `t` 격차 검사는 이것을 다 걸러내지 못한다)
- 우승 파라미터의 **이웃이 비어 있다** (상위 후보들의 `num`/`x`/`y` 가 서로 무관하게 흩어져 있으면
  격자에서 노이즈를 주운 것이다. 진짜 신호는 파라미터 이웃도 함께 양수다)

위양성이면 원장은 그대로 두고(append-only) 문서에 기록한 뒤 ①로 돌아간다.

또 하나 반드시 확인할 것: **미달 직후 탐색 공간을 넓혀 재실행하지 않았는가.** 넓힌 공간에서 나온
"달성" 은 다중검정 결과일 확률이 높다 — 검증 후보 10개면 순전히 우연으로 `t ≥ 2.0` 이 하나 나올 확률이
약 21% 다. 넓혔다면 그 사실을 보고에 **명시**한다.

### 감사를 통과했을 때

1. 확정 시뮬레이션을 남긴다 (여기서 처음으로 `pick_mstr` 에 기록된다):

   ```bash
   curl -s -X POST http://localhost:8080/api/picks/simulate \
     -H 'Content-Type: application/json' -d \
     '{"bgngYmd":"260421","endYmd":"260815","num":20,"x":1.8,"y":2.5,
       "combinedN":3,"inputMoney":1000,"algorithmCodes":["<우승 코드>"]}'
   ```

   `num`/`x`/`y`/`combinedN` 은 우승 실험의 `params` 값을 그대로 넣는다.
2. 사용자에게 보고: 알고리즘·파라미터·**검증 구간** 수익률/적중률/조합 수, 학습 대비 격차, 그리고
   `/research` 화면 링크. 루프를 멈춘다.

### 미달일 때

`diagnostics` 를 읽고 ①로 돌아간다. 단, **다음 중 하나면 멈추고 사용자에게 묻는다**:

- 같은 진단이 **3회 연속** 반복됨 → 접근 자체가 막힌 것. 목표치나 데이터 범위를 재검토해야 한다.
- 모든 분류 계열을 한 번씩 시도했고 전부 미달 → 남은 선택지는 목표 조정 또는 새 데이터 확보다.
- 검증 구간 조합 수가 계속 `minSlipCount` 에 못 미침 → 기간이 너무 짧을 수 있다.

---

## 하지 말 것

- **검증 구간 수치를 보고 파라미터를 고르기.** 그 순간 검증 구간은 두 번째 학습 구간이 된다.
  파이프라인이 학습에서만 고르고 상위 K개만 검증하는 이유가 이것이다. 우회하지 말 것.
- **시드를 바꿔 가며 재실행해 좋은 결과 낚기.** 진단을 읽고 가설을 바꾸는 것이 유일한 전진 방법이다.
- **미달 직후 파라미터 범위·후보 예산을 넓혀 재실행하기.** 시드 바꾸기와 같은 행동이다 — 격자를 넓히면
  우연히 임계값을 넘는 후보가 나올 확률만 올라간다. 범위를 넓히는 이유는 "지난번에 미달이라서" 가 아니라
  **범위가 데이터 분포 밖에 있었다** 처럼 독립적인 근거여야 하고, 그 근거를 note 에 남긴다 (§15).
- **`trainMetrics` 수치를 결과로 보고하기.**
- **탐색 중 `pick_mstr` 에 쓰기.** `/api/research/*` 는 아무것도 쓰지 않는다. 확정된 우승자만 `simulate` 한다.
- **이미 원장에 있는 조합 재탐색.**
- **원장 파일 편집·삭제.** append-only 다.
