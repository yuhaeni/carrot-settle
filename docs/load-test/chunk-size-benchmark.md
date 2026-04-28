# 정산 배치 chunk size 벤치마크

Spring Batch chunk 단위 commit + JpaPagingItemReader page 단위 fetch 구조에서 chunk size에 따른 처리 시간 트레이드오프를 측정한다.

## 측정 환경

| 항목 | 값 |
|------|---|
| OS | (TBD — `uname -a`) |
| JVM | (TBD — `java -version`) |
| 메모리 | (TBD — JVM 옵션 `-Xmx`) |
| DB | PostgreSQL 16 (Docker Compose) |
| Spring Boot | 4.0.5 |
| Spring Batch | 6.0.3 |

## 측정 절차

1. **chunk size 프로퍼티 변경** — `api/src/main/resources/application.yaml`
   ```yaml
   settle:
     batch:
       chunk-size: 100   # 측정마다 10 → 100 → 500 변경
   ```

2. **seed 데이터 생성** (현재는 수동) — 측정 대상 INCOMPLETED Settlement N건을 PAST 날짜로 INSERT. 예시:
   ```sql
   INSERT INTO settlements (seller_id, settlement_date, status, total_amount, pg_fee, platform_fee, total_fee, net_amount, skip_count, version, created_at, updated_at)
   SELECT 1, '2026-01-01', 'INCOMPLETED', 10000.00, 300.00, 500.00, 800.00, 9200.00, 0, 0, NOW(), NOW()
   FROM generate_series(1, 1000);   -- 또는 10000
   ```

3. **애플리케이션 기동**
   ```bash
   ./gradlew :api:bootRun
   ```

4. **배치 트리거 + 시간 측정**
   ```bash
   time curl -X POST http://localhost:8080/api/v1/settlements/calculate \
     -H "Content-Type: application/json" \
     -d '{"targetDate":"2026-01-02"}'
   ```
   응답 JSON의 `jobExecutionId`로 STEP_EXECUTION의 `start_time`/`end_time`을 PostgreSQL에서 조회해 정확한 처리 시간을 얻을 수 있다:
   ```sql
   SELECT step_name, status, read_count, write_count,
          start_time, end_time,
          EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 AS elapsed_ms
   FROM batch_step_execution
   WHERE job_execution_id = <jobExecutionId>;
   ```

5. **데이터 reset** (다음 측정 전)
   ```sql
   UPDATE settlements SET status = 'INCOMPLETED', skip_count = 0;
   ```

## 결과 표

> 본 표는 측정 후 채워야 한다. 측정 진행 시 `chunk_step_executions` 결과를 직접 입력.

| 데이터 건수 | chunk size | 처리 시간 (ms) | 상대 시간 (chunk=100 대비) | 메모 |
|-----------|-----------|--------------|--------------------------|------|
| 1,000     | 10        | TBD          | TBD×                     | commit 100회 |
| 1,000     | 100       | TBD          | 1.0×                     | sweet spot 후보 |
| 1,000     | 500       | TBD          | TBD×                     | 단일 트랜잭션에 가까움 |
| 10,000    | 10        | TBD          | TBD×                     | commit 1,000회 |
| 10,000    | 100       | TBD          | 1.0×                     | sweet spot 후보 |
| 10,000    | 500       | TBD          | TBD×                     | 단일 트랜잭션 보유 시간 ↑ |

### 부록 — 100K (선택)
| 데이터 건수 | chunk size | 처리 시간 (ms) | 메모 |
|-----------|-----------|--------------|------|
| 100,000   | 100       | TBD          | chunk 10은 commit 10,000회로 측정 시간이 분 단위 → 제외 |
| 100,000   | 500       | TBD          |      |

## 분석 (측정 후 작성)

### chunk 10이 느린 이유 (commit overhead)
- 트랜잭션 begin/commit + JPA flush가 매 chunk마다 발생 → I/O 비용 누적
- 데이터가 클수록 chunk 10의 페널티는 비례적으로 증가
- 예상: 1K 데이터에서 chunk 10이 chunk 100 대비 1.5~3× 느림

### chunk 500이 미묘한 이유 (트랜잭션 보유 + 메모리)
- 단일 트랜잭션 내에서 500건이 영속성 컨텍스트에 머무름 → JVM heap 압박
- Writer의 `em.flush() + em.clear()`로 매 chunk 끝에 비워주지만, 처리 중에는 유지
- chunk rollback 시 500건이 한꺼번에 재시도 → 실패 비용 증가
- 예상: chunk 100과 큰 차이 없거나 약간 느림

### 결론
- (TBD) 본 측정 결과로 **default chunk size = N**을 결정한 근거 기록
- production 트래픽 규모(일별 정산 건수)와 현재 측정 구간을 매핑

## 참고
- `application.yaml`의 `settle.batch.chunk-size`가 Step `.chunk(...)` + Reader `.pageSize(...)`에 동시 적용된다 (`SettlementBatchConfig`)
- chunk size = page size로 일치시킨 이유: chunk가 다 처리되기 전에 다음 page를 fetch하면 영속성 컨텍스트에 데이터가 누적됨. 동일하게 두면 한 chunk당 한 page fetch로 단순화
