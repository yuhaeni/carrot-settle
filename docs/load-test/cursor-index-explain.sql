-- =============================================================================
-- 인덱스 조회 (현재 상태 확인)
-- =============================================================================
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'settlements';


-- =============================================================================
-- Step 2: 현재 인덱스(idx_settlement_seller_date_status) 상태 EXPLAIN
--   - 측정 전: 10K 시드 적재 + INCOMPLETED 복원 상태에서 실행
--   - 예상: Seq Scan (cursor 쿼리는 seller_id 조건이 없어 기존 인덱스 미활용)
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
--   - ANALYZE settlements; 필수 — 통계 갱신 안 하면 planner가 stale 통계로 Seq Scan 고를 수 있음
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
-- 비교 기록용 (Plan 노드 / Execution Time / Buffers shared read / Sort 노드 유무)
--
--   현재  | Seq Scan        | ?ms | ? | ?
--   A     | ?               | ?ms | ? | ?
--   B     | ?               | ?ms | ? | ?
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
-- Step 4: 승자 인덱스 영구 적용 — 마이그레이션 파일로 작성
--   파일: infrastructure/src/main/resources/db/migration/V8__add_settlements_cursor_index.sql
--   (아래는 참고용, 실제 적용은 Flyway 마이그레이션으로)
-- =============================================================================
-- CREATE INDEX idx_settlements_cursor ON settlements (status, id);
-- 또는
-- CREATE INDEX idx_settlements_cursor ON settlements (status, settlement_date, id);
