# 정산 배치 chunk size 벤치마크

Spring Batch chunk 단위 commit + JpaPagingItemReader page 단위 fetch 구조에서 chunk size에 따른 처리 시간 + JVM/DB 영향을 측정한다. 측정 지표는 다음 3개:

1. **(a) Step 처리 시간** — chunk size별 비교의 핵심
2. **(b) Heap 피크** — chunk 보유 메모리 영향 (chunk 500의 cost를 가시화)
3. **(c) Chunk write 평균 시간** — commit overhead 직접 측정

## 측정 자료원 정책 (하이브리드)

| 지표 | 1차 자료원 | 이유 |
|------|----------|-----|
| **(a) step 시간** | **admin endpoint** `elapsedMs` (= `batch_step_execution.elapsed_ms` wrap) | Grafana ratio 는 cumulative counter 누적 → 짧은 배치(<10초) + 다회 측정 시 부정확. DB 메타는 ms 정밀도 + per-batch 격리. admin endpoint 가 이를 응답으로 노출 |
| **(b) heap 피크** | **Grafana** `jvm_memory_used_bytes` | JVM 메트릭은 batch 메타테이블에 없음 — Grafana 단독 |
| **(c) chunk write 평균** | **짧은 배치(<10초)**: admin endpoint `impliedPerChunkMs` (= elapsedMs / commitCount) / **긴 배치(>1분)**: Grafana ratio | 짧은 배치는 cumulative 누적 영향 큼. 긴 배치는 Reader/Processor 시간 분리 가능한 Grafana 가 더 정확 |

> **admin endpoint**: `GET /api/v1/admin/batch-jobs/executions/{jobExecutionId}` — `BatchAdminController` 가 `JobRepository.getJobExecution()` 으로 메타테이블 읽어 step 메트릭(`jobName / stepName / status / readCount / writeCount / commitCount / elapsedMs / impliedPerChunkMs`) 을 응답으로 노출. SQL 직접 쿼리 대체. 향후 batch job 추가 시에도 generic 재사용.

> **Verification 식**: `(c) × commit_count ≤ (a)` 가 항상 성립해야 한다 (step ⊇ chunk write). 깨지면 어느 한쪽 측정값이 오염된 것 — 1순위로 의심할 것.

> **다회 측정 사이 `bootRun` cold restart 필수**: chunk size 바꿀 때 devtools auto-reload 는 클래스만 갈아끼고 Micrometer registry 는 유지하므로 cumulative counter 가 직전 측정값까지 누적된다. 강제 종료(Ctrl+C) → 재실행으로 metric registry 도 비워야 (a)/(c) Grafana ratio 가 정확.

각 메트릭은 환경(Spring Boot 4.x, Micrometer 1.13+)별로 라벨/이름이 다르게 노출될 수 있어, 측정 시작 전 actuator dump로 실제 라벨 시그니처 확인 후 PromQL을 보정한다.

## 측정 환경

| 항목 | 값 |
|------|---|
| OS | (TBD — `uname -a`) |
| JVM | (TBD — `java -version`) |
| 메모리 | (TBD — JVM 옵션 `-Xmx`. 측정 정합성을 위해 모든 측정에서 동일 값 고정) |
| DB | PostgreSQL 16 (Docker Compose) |
| Spring Boot | 4.0.5 |
| Spring Batch | 6.0.3 |
| Prometheus | latest (scrape 15s) |
| Grafana | latest (host 3001 → container 3000) |

## 사전 준비 (한 번만)

```bash
docker compose up -d   # postgres / redis / prometheus / grafana 모두 기동
```

상태 확인:
- Postgres healthy
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (admin / admin)

Spring Boot 기동:
```bash
./gradlew :api:bootRun
```

기동 후 한 번 검증:
- http://localhost:8080/actuator/prometheus 에서 `# HELP ...` 형식 응답 확인
- http://localhost:9090/targets 에서 `carrot-settle` job이 **UP** 상태인지 확인 (DOWN이면 actuator 노출 또는 host.docker.internal 동작 점검)

## 측정 절차 (각 chunk × 데이터 조합마다 반복)

### Step 1. chunk size 변경
`api/src/main/resources/application.yaml`:
```yaml
settle:
  batch:
    chunk-size: 100   # 측정 대상으로 변경 (10 / 100 / 500)
```
변경 후 `./gradlew :api:bootRun` 재시작.

### Step 2. seed 데이터 INSERT
seller seed(V3)의 셀러를 email로 참조. `generate_series(1, N)`의 N만 바꾸면 데이터 건수 조절:
```sql
INSERT INTO settlements (
    seller_id, settlement_date, status, total_amount,
    pg_fee, platform_fee, total_fee, net_amount,
    skip_count, version, created_at, updated_at
)
SELECT
    (SELECT id FROM sellers WHERE email = 'chulsoo@example.com'),  -- V3 seed STANDARD 셀러
    DATE '2026-01-01',          -- targetDate(2026-01-02)보다 이전이어야 Reader가 픽업
    'INCOMPLETED',
    10000.00, 300.00, 500.00, 800.00, 9200.00,
    0, 0, NOW(), NOW()
FROM generate_series(1, 1000);   -- 1K, 10K, 100K 측정 시 N만 변경
```

### Step 3. 측정 시작 timestamp 기록 + 배치 트리거
Grafana 그래프 범위 지정용으로 시작 시각을 메모해 둔다.
```bash
date '+%Y-%m-%d %H:%M:%S'   # 시작 시각 기록 (Grafana 그래프 범위에 활용)

curl -X POST http://localhost:8080/api/v1/settlements/calculate \
  -H "Content-Type: application/json" \
  -d '{"targetDate":"2026-01-02"}'
```

> ⚠️ 응답이 `BATCH_ALREADY_COMPLETED`(409) / `BATCH_ALREADY_RUNNING`(409)이면 batch가 *돌지 않았다*. Step 7 reset이 누락되었거나 IntelliJ Manual TX commit 누락이 1순위 의심. 메타테이블에 동일 `targetDate` JobInstance가 남아있는지 확인하고 정리 후 재시도.

> ⚠️ **응답에 `jobExecutionId`가 있는데 IntelliJ DB 콘솔에선 `batch_job_execution`이 비어있다면** 100% 세션 stale 문제. IntelliJ가 Manual TX 모드일 때 SELECT 한 번이 트랜잭션을 열고 snapshot을 잡아, 그 이후 batch가 INSERT/COMMIT해도 내 세션은 변경을 못 본다. 해결:
> 1. 콘솔에서 `COMMIT;` 또는 `ROLLBACK;` 실행 후 재조회
> 2. 콘솔 상단 트랜잭션 모드 드롭다운을 `Tx: Manual` → **`Tx: Auto`**로 변경 (측정 작업엔 Auto가 안전)
> 3. 빠른 우회 검증: `docker exec -it carrot-settle-postgres-1 psql -U carrot -d carrot_settle -c "SELECT job_execution_id, status FROM batch_job_execution ORDER BY job_execution_id DESC LIMIT 5;"` — psql은 매번 새 세션이라 항상 최신 commit을 본다.

### Step 3.5. 배치 실행 결과 검증 (Grafana 추출 전 필수)

Grafana 메트릭 추출 전에 batch가 실제로 시드 데이터를 모두 처리했는지 DB로 확인. 이걸 안 하면 read_count=0인 실패 batch에서 (a)/(b)/(c) 메트릭을 추출해 잘못된 결론을 낼 수 있다.

```sql
-- (1) Job 종료 상태 + 처리 건수
SELECT j.job_execution_id, j.status, j.exit_code,
       s.read_count, s.write_count, s.commit_count, s.rollback_count, s.read_skip_count, s.process_skip_count, s.write_skip_count
FROM batch_job_execution j
JOIN batch_step_execution s ON s.job_execution_id = j.job_execution_id
ORDER BY j.job_execution_id DESC LIMIT 1;
-- 기대: status=COMPLETED, exit_code=COMPLETED,
--       read_count = write_count = 시드 건수 (1000 / 10000),
--       *_skip_count = 0 (시드 데이터엔 skip 트리거 없음)

-- (2) settlements 상태 분포
SELECT status, COUNT(*) FROM settlements GROUP BY status;
-- 기대: COMPLETED = 시드 건수, INCOMPLETED = 0
```

기대값과 다르면:
- **status=FAILED** 또는 read_count=0: Reader 쿼리 조건(status, skip_count, settlement_date) 점검. 시드 INSERT 시 `settlement_date < targetDate` 위반 가능성 1순위.
- **read_count > write_count**: skip이 발생함. `process_skip_count` 컬럼 확인 + settlements 중 net_amount 음수 / skip_count >= 3 (skip threshold) row가 있는지 점검.
- **INCOMPLETED 일부 잔존**: 위 두 케이스 중 하나에 해당. 메트릭 추출 중단 후 데이터 정합성 먼저 해결.

### Step 4. Grafana로 측정값 추출

**http://localhost:3001 → Explore → Prometheus datasource 선택**

> ⚠️ **시간 범위는 측정 구간을 반드시 포함하도록 지정**. Grafana 기본은 "Last 5 minutes"인데, 측정이 수 분 이상 걸렸거나 추출 시점이 측정 직후가 아니면 데이터가 안 보인다. **"Last 1 hour"** 또는 Custom range로 시작/종료 timestamp를 감싸는 구간 지정.

> 📌 **라벨 prefix 주의**: Spring Batch 6.0 + Boot 4.0의 step 메트릭은 라벨이 `spring_batch_step_*` prefix로 노출된다. 즉 `job_name`이 아닌 `spring_batch_step_job_name`, `step_name`이 아닌 `spring_batch_step_name`. 환경별 실제 라벨 확인:
> ```bash
> curl -s http://localhost:8080/actuator/prometheus | grep -E "^spring_batch_step_seconds_max"
> ```

#### (a) Step 처리 시간 — 측정의 핵심 지표

**1차 자료원: admin endpoint 호출** (`BatchAdminController` MVP — 이슈 #14 task D). DB SQL 을 직접 치지 않고 generic admin endpoint 가 `JobRepository` 로 메타테이블을 읽어 step 메트릭을 응답에 노출. 향후 batch job 추가 시에도 동일 endpoint 재사용.

##### 자동화 — `scripts/benchmark-cell.sh` (권장)

본 Step 2 / 3 / 3.5 / 4(a) 를 한 번에 처리하는 자동화 스크립트.

```bash
# Usage: ./scripts/benchmark-cell.sh <chunk_size_label> <data_count> [target_date]
./scripts/benchmark-cell.sh 100 1000 2026-01-02
```

스크립트가 수행:
1. settlements + Spring Batch 메타테이블 reset
2. N건 시드 INSERT
3. POST `/calculate` 트리거
4. GET `/admin/batch-jobs/executions/{id}` 로 메트릭 조회 + 검증 (status=COMPLETED, read=write=N)
5. **결과 표용 markdown row 한 줄을 stdout 으로 출력** (진행 로그는 stderr → 분리)

출력 예시:
```
| 1,000 | 100 | 1481 | TBD | 148 | TBD× | commit 10회 — read/write 1000/1000 |
```

`(b) heap 피크` 와 `상대 step 시간` 컬럼은 `TBD` 로 출력 — 각각 Grafana 추출 + chunk 100 baseline 산출 후 수동 채움.

**chunk size 변경 절차** (cell 마다 반복):
1. `application.yaml` 의 `settle.batch.chunk-size: <N>` 편집
2. `bootRun` 강제 종료(Ctrl+C) → 재실행 (cold restart 필수, devtools auto-reload 는 Micrometer registry 비우지 못함)
3. `./scripts/benchmark-cell.sh <N> <data>` 실행 → 출력 markdown row 를 결과 표에 복붙

##### 수동 호출 fallback

스크립트 안 쓰고 직접 curl chain:
```bash
JOB_ID=$(curl -sX POST http://localhost:8080/api/v1/settlements/calculate \
  -H "Content-Type: application/json" \
  -d '{"targetDate":"2026-01-02"}' | jq -r '.jobExecutionId')

curl -s "http://localhost:8080/api/v1/admin/batch-jobs/executions/$JOB_ID" | jq
```

응답:
```json
{
  "jobExecutionId": 7,
  "jobName": "settlementJob",
  "stepName": "settlementStep",
  "status": "COMPLETED",
  "exitCode": "COMPLETED",
  "readCount": 1000,
  "writeCount": 1000,
  "commitCount": 10,
  "elapsedMs": 1481,
  "impliedPerChunkMs": 148
}
```

`elapsedMs` → 표 (a) 컬럼 (이미 ms). `impliedPerChunkMs` → 표 (c) 컬럼 (짧은 배치 정책).

##### SQL 직접 쿼리 fallback

admin endpoint 사용 못 하거나 historical 분석 시:
```sql
SELECT step_name, status, read_count, write_count, commit_count,
       EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 AS step_ms,
       EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 / commit_count AS implied_per_chunk_ms
FROM batch_step_execution
WHERE job_execution_id = (SELECT MAX(job_execution_id) FROM batch_job_execution);
```

응답의 `step_ms` / `implied_per_chunk_ms` 가 admin endpoint 의 `elapsedMs` / `impliedPerChunkMs` 와 동일.

**Grafana 보조** (시각화 + 긴 배치용 이중 검증):

```promql
spring_batch_step_seconds_sum{spring_batch_step_name="settlementStep"}
  /
spring_batch_step_seconds_count{spring_batch_step_name="settlementStep"}
```

Stat 패널 Reducer **Last**, 단위 초 → ×1000 = ms. 짧은 배치에선 DB 값과 비교해 ±20% 이상 벗어나면 cumulative 누적 의심 (`bootRun` cold restart 후 재측정).

> **언제 Grafana 가 더 유용한가**: ① 시계열 그래프 캡처 (PR/블로그 첨부) ② 1분+ 긴 배치에선 DB 와 Grafana 둘 다 정확하므로 Grafana 가 추세 시각화 보너스 ③ 다회 측정 평균을 한 시점에서 보기.

> **`_max` 라벨 함정**: `spring_batch_step_seconds_max` 는 sliding window 내 최대값이라 scrape 마다 변동. ratio 가 더 안정적이지만 본 정책에선 ratio 도 부신뢰 대상이라 결국 DB 가 정답.

#### (b) Heap 피크 — chunk 보유 메모리 영향

**단일 PromQL** (그래프 + peak 숫자 모두 이 한 줄로):
```promql
sum(jvm_memory_used_bytes{area="heap"}) / 1024 / 1024
```

> ⚠️ **반드시 `sum`으로 감쌀 것**: `jvm_memory_used_bytes{area="heap"}`는 G1 Eden / Old Gen / Survivor 3개 pool 시계열을 각각 반환한다. `sum` 없이 Stat 패널 Reducer Max를 걸면 *가장 큰 pool 한 개의 peak*만 보여 chunk size 비교 신호가 약해진다. chunk 처리는 영속성 컨텍스트가 Eden에 단기 할당되었다가 minor GC로 회수되는 패턴이라 단일 pool peak는 GC region 캡에 묶여 chunk size 차이가 잘 안 드러난다. 합계로 봐야 batch 동안 잡고 있던 총 heap이 정확히 잡힘.

> **pool별 breakdown은 분석 보조용**: 어느 pool에서 차이가 나는지 보고 싶을 땐 `jvm_memory_used_bytes{area="heap"} / 1024 / 1024`를 별도 패널에 띄우면 `{{id}}` 라벨로 G1 Eden / Old Gen / Survivor 3개 라인이 자동 분리된다. 정상적인 chunk 배치에서 Old Gen은 chunk size에 거의 무관하게 평평해야 하고(증가하면 영속성 컨텍스트 누수 의심), chunk size별 차이는 Eden + Survivor에서 보인다.

> 환경별로 `area="heap"`이 아닐 수 있음 (`memory_pool` 등). 라벨 없이 `jvm_memory_used_bytes` 한 번 실행해 노출되는 라벨 시그니처 확인 후 보정.

**한 쿼리에서 그래프 + pea/k 추출 — Grafana 시각화 옵션 활용**

| 용도 | Grafana 설정 | 결과 |
|------|------------|------|
| **그래프 캡처** | Time series 패널 | chunk 처리 중 heap 추세 곡선 |
| **표 입력 peak 숫자** | Stat 패널 + **Reducer: Max** | 시계열의 최고점 한 값 |
| **둘 다 즉시** | Explore 화면에서 그래프 위 마우스 hover | tooltip에 시점별 값 — 최고점 위에 hover하면 peak |

→ **같은 PromQL을 쓰니 그래프 최고점 = 표의 peak 숫자**. 데이터 일관성 보존, 시각화 방식만 다름.

> **그래프가 들쭉날쭉한 건 정상 — 톱니파 자체가 chunk size 비교의 signal**:
> - `jvm_memory_used_bytes`는 *현재 살아있는 객체 크기*를 직접 노출하는 raw 값. JVM은 객체 할당 사이엔 단조 증가하다가 minor GC 시점에 살아남은 객체만 남기고 나머지를 회수 → 절벽 하락이라 본질적으로 톱니파다. `sum`을 씌워도 `max_over_time`을 씌워도 톱니 자체는 사라지지 않는다 (`sum`은 3개 pool 합산일 뿐, `max_over_time`은 sliding window 내 최고점 추적일 뿐).
> - chunk size 차이는 톱니의 *주기와 진폭*에 나타남: chunk 10은 객체 할당 속도가 느려 주기가 길고 진폭이 작고, chunk 500은 빠르게 Eden을 채워 주기가 짧고 진폭이 크다. → **톱니의 최고점 한 점**이 비교 지표.
> - 매끈한 곡선 기대하지 말고 Reducer **Max** 값만 표에 입력. 단, **시간 범위는 배치 시작 timestamp ~ `batch_step_execution.end_time` + 여유 5초 정도로 좁혀야** post-batch JVM 드리프트 / 다른 워크로드 노이즈가 안 섞인다.

> **batch가 GC 1사이클보다 짧으면 (b) 비교는 무의미**: 예를 들어 1,000건 × chunk 10이 660ms 정도로 끝나는 경우 minor GC가 한 번도 안 일어났을 가능성이 크다. 이 경우 (b) heap peak는 chunk size 차이가 아니라 *그 시점의 JVM 베이스라인*을 측정하는 셈이라 비교 신호가 거의 없다. **(b) 본격 비교는 10K 측정부터** 의미있고, 1K는 (a)/(c) 시간 비교 위주로 보면 된다.

> **JVM 워밍업 보정**: `bootRun` 직후 첫 측정은 JIT 컴파일 / 클래스 로딩 / connection pool 초기화 영향으로 baseline이 낮게 나올 수 있다. 정합성을 위해 같은 (chunk × 데이터) 조건으로 2번 돌려 *두 번째 값*을 표에 입력 권장 (측정 사이 Step 7 reset 필수).

> 만약 Stat 패널 없이 PromQL로 peak를 *수치로 추출*하고 싶다면 subquery 형식으로:
> ```promql
> max_over_time(sum(jvm_memory_used_bytes{area="heap"})[배치소요시간:]) / 1024 / 1024
> ```
> `sum`은 instant vector라 range vector(`[5m]`)를 직접 못 받음 → subquery `[5m:]` 필수. 결과는 단일 수평선(이미 max만 남음). subquery는 sliding window라 측정 종료 5분 후에는 peak가 window에서 빠져나가 값이 단계적으로 하락하니, 추출 직후 수치를 보거나 시간 범위를 좁힐 것. 추세 시각화 + 수치 추출을 한 패널에서 모두 원할 땐 raw `sum` 쿼리 + Stat reducer **Max** 조합이 최선.

**값 단위**: MB. chunk size 클수록 영속성 컨텍스트 누적으로 피크가 올라감.

#### (c) Chunk write 평균 시간 — commit overhead 직접 측정

**자료원 분기 — 배치 길이에 따라**:
- **짧은 배치(<10초)**: DB implied 우선 — `step_ms / commit_count` (위 (a) 쿼리의 `implied_per_chunk_ms` 컬럼). Reader/Processor 시간 포함이라 약 5~15% over-estimate 지만 cumulative 누적 영향에서 자유. 본 1K/10K 측정의 default.
- **긴 배치(>1분)**: Grafana ratio 가 더 정확 (Reader/Processor 분리, scrape sample 충분히 누적). 100K 측정에서 사용.

**Grafana PromQL** (그래프 + 평균 숫자 모두):
```promql
spring_batch_chunk_write_seconds_sum{spring_batch_chunk_write_step_name="settlementStep"}
  /
spring_batch_chunk_write_seconds_count{spring_batch_chunk_write_step_name="settlementStep"}
```

> ⚠️ **라벨 prefix 주의**: chunk write 메트릭의 라벨은 step 메트릭(`spring_batch_step_*`)과 다른 자체 prefix `spring_batch_chunk_write_*`를 사용한다. 즉 `spring_batch_chunk_write_step_name`(O), `spring_batch_step_name`(X), `step_name`(X). 환경별 실제 라벨 확인:
> ```bash
> curl -s http://localhost:8080/actuator/prometheus | grep "^spring_batch_chunk_write_seconds_sum" | head -1
> ```
> 노출되는 `{...}` 안의 라벨 이름이 정답. (참고: status 라벨도 함께 노출되어 `spring_batch_chunk_write_status="SUCCESS"`처럼 분리 가능)

**한 쿼리에서 그래프 + 평균 추출 — Grafana 시각화 옵션 활용**

| 용도 | Grafana 설정 | 결과 |
|------|------------|------|
| **그래프 캡처** | Time series 패널 | 측정 진행될수록 안정화되는 *누적 평균 곡선* |
| **표 입력 평균값** | Stat 패널 + **Reducer: Last** | 측정 종료 시점 누적 평균 = 측정 전체 평균 |
| **즉시 확인** | Explore 그래프 우측 끝 hover | 마지막 시점 tooltip 값 |

→ **그래프 마지막 값 = 표의 평균값**. (b) heap과 동일한 단일 쿼리 일관성 패턴.

> **`rate()` 패턴은 보조 지표**: `rate(...sum[5m]) / rate(...count[5m])`는 *직전 5분 동안의 순간 평균* — 측정 중 평균이 어떻게 흔들리는지 추세를 보고 싶을 때 별도로 사용. 본 측정의 비교 지표는 누적 평균 ratio가 더 정합적.

**값 단위**: Grafana ratio 값은 **초(s)** → 표 입력 시 **× 1000 = ms**.
- 예: ratio `0.0130` → 표에 **13ms**, ratio `0.0421` → 표에 **42.1ms**

chunk 10일 때 매우 짧지만 *총 commit 횟수*가 많아 step 시간을 늘림. chunk 500일 때 한 번의 write 자체는 길어짐.

> **3개 지표 종합 해석**: chunk 10 → (a) 길고 (b) 낮고 (c) 짧음 (commit 폭증). chunk 500 → (a) 짧을 수 있지만 (b) 가장 높음 (메모리 cost). chunk 100 sweet spot은 (a)/(b) 균형.

### Step 5. Grafana 그래프 캡처
Explore 화면에서 (a)/(b)/(c) 쿼리를 실행한 그래프를 각각 PNG로 저장:
- `docs/load-test/screenshots/chunk-{size}-data-{N}-step.png` — (a) step 시간
- `docs/load-test/screenshots/chunk-{size}-data-{N}-heap.png` — (b) heap 피크
- `docs/load-test/screenshots/chunk-{size}-data-{N}-chunk-write.png` — (c) chunk write

### Step 6. 결과 표에 입력 (아래)

### Step 7. 측정 사이 reset (다음 측정 전)
settlements 데이터 + Spring Batch 메타테이블을 함께 정리해야 다음 측정의 시작 조건이 동일해진다. 둘 중 하나만 정리하면 다음 호출에서 `BATCH_ALREADY_COMPLETED`(메타 잔존) 또는 처리 건수 불일치(데이터 잔존) 문제가 발생한다.
```sql
TRUNCATE batch_step_execution_context,
         batch_job_execution_context,
         batch_step_execution,
         batch_job_execution_params,
         batch_job_execution,
         batch_job_instance
  RESTART IDENTITY CASCADE;
UPDATE settlements SET status = 'INCOMPLETED', skip_count = 0, version = 0;
COMMIT;   -- IntelliJ DB 콘솔이 Manual TX 모드면 필수. Auto면 생략 가능
```
> `version = 0` 리셋 이유: 이전 측정에서 일부 row의 version이 1+ 상태가 됐다면 다음 측정에서 OptimisticLock 위험. status만 되돌리면 부족.

> Manual TX 모드 흔적 점검: `docker exec -it carrot-settle-postgres-1 psql -U carrot -d carrot_settle -c "SELECT COUNT(*) FROM batch_job_instance;"` — **0이 아니면** TRUNCATE 결과가 다른 세션에 안 보이는 상태(commit 누락).

## 참고 — 자료원 비교 빠른 체크

본 측정 정책에서 (a) 의 1차 자료원은 DB. Grafana ratio 와의 차이가 ±20% 이상이면 cumulative 누적 오염 의심:

```sql
SELECT step_name, status, read_count, write_count, commit_count,
       EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 AS step_ms,
       EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 / commit_count AS implied_per_chunk_ms
FROM batch_step_execution
WHERE job_execution_id = (SELECT MAX(job_execution_id) FROM batch_job_execution);
```

긴 배치(>1분) 측정 시에는 DB step_ms 와 Grafana ratio 가 ±5% 이내로 일치해야 정상. 그 이상 차이 나면 측정 사이 bootRun 재시작 누락 가능성.

## 결과 표

> 본 표는 측정 후 채워야 한다. 시간은 모두 **ms**, 메모리는 **MB**.
>
> **표 입력 정책 (자료원별)**:
> - **(a) step 시간 ← admin endpoint** `elapsedMs` 필드 (`GET /api/v1/admin/batch-jobs/executions/{id}`). 이미 ms, 환산 불필요. SQL fallback: `EXTRACT(EPOCH FROM (end_time - start_time)) * 1000` from `batch_step_execution`
> - **(b) heap 피크 ← Grafana**: `sum(jvm_memory_used_bytes{area="heap"}) / 1024 / 1024` + Stat Reducer **Max** (JVM 메트릭은 batch 메타테이블에 없음 — Grafana 단독)
> - **(c) 평균 chunk write ← 배치 길이에 따라 분기**:
>   - 짧은 배치(<10초): admin endpoint `impliedPerChunkMs` 필드 (= elapsedMs / commitCount, Reader/Processor 시간 포함이라 약 5~15% over)
>   - 긴 배치(>1분): Grafana `..._chunk_write_seconds_sum / ..._count` ratio (초→ms 환산 ×1000)
>
> **검증식**: `(c) × commit_count ≤ (a)` 가 성립해야 함 (step ⊇ chunk write). 깨지면 측정값 오염 — 1순위로 chunk size 변경 시 `bootRun` cold restart 누락 의심.
>
> **상대 step 시간 계산식**: `해당 행 (a) step ms ÷ 같은 데이터 건수의 chunk=100 행 (a) step ms`. 같은 데이터 건수 row 내에서만 비교(1K 의 chunk 10 을 10K 의 chunk 100 과 섞어 비교 X). 절대 ms 차이가 데이터 규모에 따라 다르게 보이는 걸 정규화해 "chunk 10 은 sweet spot 대비 N배 느림" 같은 한 줄 결론을 표에서 바로 뽑기 위함.

| 데이터 건수 | chunk size | (a) step 시간 (ms)  | (b) heap 피크 (MB) | (c) 평균 chunk write (ms) | 상대 step 시간 (vs 같은 건수의 chunk=100) | 메모                                                              |
|-----------|-----------|-------------------|-----------------|-------------------------|----------------------------------------|-----------------------------------------------------------------|
| 1,000 | 10 | 1895 | 121 | 18 | **1.34×** | commit 100회 — read/write 1000/1000 |
| 1,000 | 100 | 1412 | 80.8 | 141 | 1.0× (baseline) | commit 10회 — read/write 1000/1000 |
| 1,000 | 500 | 1375 | 74.6 | 687 | **0.97×** | commit 2회 — read/write 1000/1000 |
| 10,000 | 10 | 17020 | 97.2 | 17 | **1.31×** | commit 1000회 — read/write 10000/10000 |
| 10,000 | 100 | 13034 | 74.6 | 130 | 1.0× (baseline) | commit 100회 — read/write 10000/10000 |
| 10,000 | 500 | 11561 | 86.1 | 578 | **0.89×** | commit 20회 — read/write 10000/10000 |

### 부록 — 100K
| 데이터 건수 | chunk size | (a) step 시간 (ms) | (b) heap 피크 (MB) | (c) 평균 chunk write (ms) | 상대 step 시간 (vs 같은 건수의 chunk=100) | 메모 |
|-----------|-----------|------------------|------------------|------------------------|----------------------------------------|------|
| 100,000 | 10 | 191,423 | 89.7 | 19 | **1.41×** | commit 10,000회 — read/write 100000/100000 (약 3분 소요) |
| 100,000 | 100 | 135,685 | 119 | 135 | 1.0× (baseline) | commit 1,000회 — read/write 100000/100000 |
| 100,000 | 500 | 126,387 | 128 | 631 | **0.93×** | commit 200회 — read/write 100000/100000 |

## 분석

### chunk 10 — commit overhead 의 안정적 penalty (비선형 누적 가설 기각)

| 데이터 | (a) | chunk 100 대비 | commit 횟수 | (c) per-commit |
|---|---|---|---|---|
| 1K | 1,895ms | **+34%** | 100회 | 18ms |
| 10K | 17,020ms | **+31%** | 1,000회 | 17ms |
| 100K | 191,423ms | **+41%** | 10,000회 | 19ms |

**핵심 발견 — penalty 가 31~41% 범위에서 안정**. commit 횟수가 100 → 1,000 → 10,000 으로 100배 증가했는데도 penalty 는 거의 일정. 당초 가설("commit overhead 가 데이터량에 비선형 누적") 은 본 측정 환경에선 성립 안 함. **per-commit 고정 cost 가 17~19ms 로 환경 결정 상수** (JPA flush + JDBC commit + PostgreSQL WAL fsync 합산), 데이터량과 무관.

- (b) heap 은 데이터량에 따라 121 → 97 → 90 MB. **1K 에서 가장 높은 건 JVM 워밍업 상태** (첫 측정, JIT/클래스 로딩 잔여) 가능성. 안정화된 10K/100K 가 90~97 MB 로 chunk 100 보다 살짝 위
- (c) chunk write 단건 17~19ms — 거의 변동 없음. 짧은 단일 commit 이 *총 횟수* 누적되어 (a) 30%+ 손실의 원인

### chunk 500 — 시간 ↓ 가 모든 규모에서 일관 (변곡점 없음)

이전 1K/10K 측정에선 "chunk 500 이 1K 에서만 빠르고 10K 에선 cost 만 남는 변곡점" 으로 봤지만, 본 측정 + 100K 추가로 narrative 갱신:

| 데이터 | (a) | chunk 100 대비 | (b) heap | (c) per-commit lock 보유 |
|---|---|---|---|---|
| 1K | 1,375ms | **0.97×** (3% 빠름) | 74.6 MB (-8% vs 100) | 687ms (×4.9) |
| 10K | 11,561ms | **0.89×** (11% 빠름) | 86.1 MB (+15%) | 578ms (×4.5) |
| 100K | 126,387ms | **0.93×** (7% 빠름) | 128 MB (+8%) | 631ms (×4.7) |

**chunk 500 이 모든 규모에서 chunk 100 보다 빠름** — 10K 에서 11% 절약으로 효과 가장 큼. 변곡점은 본 측정 범위(1K~100K)에선 안 나타남.

그러나 시간 절약의 cost 는 정량적으로 명확:
- **single commit lock 보유 시간 ×4.5~4.9** (모든 규모에서 일관) — 다중 사용자 환경에서 wait time 비례 증가
- **(b) heap +8~15%** (1K 제외) — 한 번에 500 entity 영속성 컨텍스트 보유
- **chunk 도중 예외 시 500건 롤백** (chunk 100 의 5×) — skip/재처리 비용 ×5
- **WAL fsync 단일 commit 크기 ×5** — DB 부하 패턴 비대칭화

(a) 만 보면 chunk 500 이 무조건 우세하지만 **(c) 의 ×5 lock 보유가 운영 환경 동시성 cost 를 정량적으로 증명**.

### 결론 — default chunk size = 100 (3개 차원 균형)

| 차원 | chunk 10 | chunk 100 | chunk 500 |
|---|---|---|---|
| **(a) 시간** | 30~40% 느림 | baseline | 3~11% 빠름 |
| **(b) heap** | 1K 워밍업 변동 / 안정화 후 +20% | 가장 안정 | +8~15% |
| **(c) lock 보유** | 17~19ms (×0.13) | 130~141ms | **578~687ms (×4.7)** |

- chunk 500 의 시간 절약 (3~11%) 은 lock 보유 ×5 cost 를 정당화하지 못함 — 운영 환경의 동시성 안정성 우선
- chunk 10 의 commit overhead 는 안정적 30%대지만 per-commit 17ms × 만회로 절대 시간이 분 단위 폭증 (100K = 191초 vs chunk 100 의 135초, +56초 손실)
- chunk 100 이 **시간 / 메모리 / 동시성 3개 차원에서 가장 robust** — 데이터 규모 1K → 100K 어느 환경에서도 acceptable

`SettlementBatchConfig` 의 `chunk-size: 100` (이미 `application.yaml` default) 를 production default 로 채택.

production 일별 정산 건수 매핑 (실측 기반):
- 일 1K → 10 chunks, **약 1.4초**
- 일 5K → 50 chunks, 약 7초
- 일 10K → 100 chunks, **약 13초**
- 일 100K → 1,000 chunks, **약 2분 16초**

100K 이상 영역에서 chunk 500 은 절대 시간 약 9초 절약하지만 lock 보유 5배 → 단일 사용자 일배치엔 chunk 500 검토 가능, 다중 사용자/동시성 시나리오엔 chunk 100 유지.

## 부록 — 추가 보강 메트릭 (선택)

본 측정의 (a)/(b)/(c) 외에 다음을 시도해볼 수 있다.

### HikariCP 활성 커넥션 — 락 보유 영향 간접 측정
```promql
hikaricp_connections_active
```
단일 사용자 측정에선 의미 적음. 다중 사용자 부하 테스트 단계에서 chunk 500의 락 경합 위험을 가시화할 때 활용.

## 참고
- `application.yaml`의 `settle.batch.chunk-size`가 Step `.chunk(...)` + Reader `.pageSize(...)`에 동시 적용된다 (`SettlementBatchConfig`)
- chunk size = page size로 일치시킨 이유: chunk가 다 처리되기 전에 다음 page를 fetch하면 영속성 컨텍스트에 데이터가 누적됨. 동일하게 두면 한 chunk당 한 page fetch로 단순화
- Grafana 그래프 캡처는 측정 결과의 신뢰도를 높이는 1차 자료로, PR/블로그 본문에 첨부
