-- =============================================================================
-- cursor 페이징 쿼리 인덱스 검토 — 이슈 #14 5번-A
--
-- 결론 (2026-05-11 측정): **추가 인덱스 불필요**.
--   PK btree(`settlements_pkey`)가 cursor 패턴(`id > X ORDER BY id LIMIT N`)을
--   자연스럽게 처리. Execution Time 1.7ms / Buffers 6 페이지로 충분히 빠름.
--   누적 운영 후(COMPLETED 다수 + INCOMPLETED 소수) 시나리오는 별도 모니터링
--   영역 — `(a) step ms` 가 시간 따라 증가하면 그때 재검토.
-- =============================================================================


-- =============================================================================
-- 인덱스 조회 (현재 상태 확인)
-- =============================================================================
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'settlements';


-- =============================================================================
-- Step 2: 현재 인덱스 상태 EXPLAIN
--   - 측정 전: 10K 시드 적재 + INCOMPLETED 복원 상태에서 실행 (batch 트리거 직전 상태)
--   - 예상: PK btree(settlements_pkey)로 Index Scan
--     cursor 패턴(id > X ORDER BY id LIMIT N)은 PK 인덱스로 자연스럽게 처리됨.
--     기존 (seller_id, settlement_date, status) 인덱스는 cursor 쿼리에 seller_id 조건이
--     없어 미활용이지만, PK btree가 그 역할을 대신함.
-- =============================================================================
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM settlements
WHERE status = 'INCOMPLETED'
  AND skip_count < 3
  AND settlement_date < '2026-05-01'
  AND id > 0
ORDER BY id
LIMIT 100;


-- =============================================================================
-- Step 3: 후보 인덱스 격리 비교
--   - BEGIN/ROLLBACK으로 격리 (둘 동시 존재 시 planner가 한쪽만 선택해 비교 불가)
--   - ANALYZE settlements; 필수 — 통계 갱신 안 하면 planner가 stale 통계로 잘못된 plan 선택
--   - 본 측정에선 Step 2 결과(1.7ms)가 이미 최적이라 Step 3는 reference 용
-- =============================================================================

-- ---- 후보 A: (status, settlement_date, id) ----------------------------------
BEGIN;
CREATE INDEX idx_cursor_a ON settlements (status, settlement_date, id);
ANALYZE settlements;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM settlements
WHERE status = 'INCOMPLETED'
  AND skip_count < 3
  AND settlement_date < '2026-05-01'
  AND id > 0
ORDER BY id
LIMIT 100;
ROLLBACK;


-- ---- 후보 B: (status, id) ---------------------------------------------------
BEGIN;
CREATE INDEX idx_cursor_b ON settlements (status, id);
ANALYZE settlements;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM settlements
WHERE status = 'INCOMPLETED'
  AND skip_count < 3
  AND settlement_date < '2026-05-01'
  AND id > 0
ORDER BY id
LIMIT 100;
ROLLBACK;


-- =============================================================================
-- 측정 결과 (2026-05-11, 10K INCOMPLETED 시드)
--
--   현재  | Index Scan on settlements_pkey | 1.695 ms | 6 buffers | (Sort 노드 없음)
--   A     | (미측정 — 현재로 충분)         |          |           |
--   B     | (미측정 — 현재로 충분)         |          |           |
--
-- 의사결정: 현재 PK Scan이 측정 노이즈 수준(1.7ms)이라 후보 A/B 비교 불필요.
-- 추가 인덱스는 INSERT 부하 + 디스크 cost만 증가시키고 cursor 쿼리 개선 효과 미미할 가능성 높음.
-- =============================================================================


-- =============================================================================
-- Step 1 검증용: cursor 수정 후 read_count == write_count == 시드 건수 확인
--   - 매 측정 후 실행하여 데이터 누락 없음 검증
-- =============================================================================
SELECT step_name,
       read_count,
       write_count,
       commit_count,
       end_time - start_time AS elapsed
FROM batch_step_execution
WHERE step_execution_id = (SELECT MAX(step_execution_id) FROM batch_step_execution);


-- =============================================================================
-- Step 4: 향후 재검토 트리거 — 운영 누적 시 재측정 기준
--
-- 운영 6개월~1년 후 settlements 테이블에 COMPLETED 누적이 많아지면 cursor 쿼리의
-- PK Scan이 historical COMPLETED 대량 walk → filter discard 패턴으로 비효율화 가능.
-- 다음 조건 중 하나라도 충족 시 본 인덱스 검토 재개:
--
--   (1) `(a) step ms` (admin endpoint elapsedMs) 가 baseline 대비 5x+ 증가
--   (2) batch_step_execution 의 평균 elapsed_ms 가 1분 초과
--   (3) settlements 테이블 row count > 1M 이면서 COMPLETED 비율 > 95%
--
-- 재검토 시 후보:
--   CREATE INDEX idx_settlements_cursor ON settlements (status, id);                    -- B
--   CREATE INDEX idx_settlements_cursor ON settlements (status, settlement_date, id);   -- A
--
-- 마이그레이션 파일: infrastructure/src/main/resources/db/migration/V8__add_settlements_cursor_index.sql
-- =============================================================================
