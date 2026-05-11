-- Step 3.5. 배치 실행 결과 검증 (Grafana 추출 전 필수)

-- Grafana 메트릭 추출 전에 batch가 실제로 시드 데이터를 모두 처리했는지 DB로 확인. 이걸 안 하면 read_count=0인 실패 batch에서 (a)/(b)/(c) 메트릭을 추출해 잘못된 결론을 낼 수 있다.

-- (1) Job 종료 상태 + 처리 건수
SELECT j.job_execution_id, j.status, j.exit_code,
       s.read_count, s.write_count, s.commit_count, s.rollback_count, s.read_skip_count, s.process_skip_count, s.write_skip_count
FROM batch_job_execution j
         JOIN batch_step_execution s ON s.job_execution_id = j.job_execution_id
ORDER BY j.job_execution_id DESC LIMIT 1;
-- 기대: status=COMPLETED, exit_code=COMPLETED,
--       read_count = write_count = 시드 건수 (1000 / 10000),
--       *_skip_count = 0 (시드 데이터엔 skip 트리거 없음)


SELECT job_execution_id, status, exit_code, create_time
FROM batch_job_execution
ORDER BY job_execution_id DESC LIMIT 5;
-- settlements 상태 분포
SELECT status, COUNT(*) FROM settlements GROUP BY status;
-- 기대: COMPLETED = 시드 건수, INCOMPLETED = 0

-- 배치 초기화
TRUNCATE batch_step_execution_context,
    batch_job_execution_context,
    batch_step_execution,
    batch_job_execution_params,
    batch_job_execution,
    batch_job_instance
    RESTART IDENTITY CASCADE;
UPDATE settlements SET status = 'INCOMPLETED', skip_count = 0, version = 0;
COMMIT;   -- IntelliJ DB 콘솔이 Manual TX 모드면 필수. Auto면 생략 가능


-- 치가 너무 빨라 보일 때는 read_count == write_count == 시드건수 확인이 1순위
SELECT j.job_execution_id, j.status, j.exit_code,
       s.read_count, s.write_count, s.commit_count,
       EXTRACT(EPOCH FROM (s.end_time - s.start_time)) * 1000 AS elapsed_ms
FROM batch_job_execution j
         JOIN batch_step_execution s ON s.job_execution_id = j.job_execution_id
ORDER BY j.job_execution_id DESC LIMIT 1;

-- 측정 직후 이 한 쿼리로 Grafana 시간 범위 추출
SELECT
    start_time - INTERVAL '5 seconds' AS grafana_from,
    end_time + INTERVAL '30 seconds' AS grafana_to,
    EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 AS elapsed_ms
FROM batch_step_execution
WHERE job_execution_id = (SELECT MAX(job_execution_id) FROM batch_job_execution);


SELECT step_name, status, read_count, write_count, commit_count,
       EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 AS step_ms,
       EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 / commit_count AS implied_per_chunk_ms
FROM batch_step_execution
WHERE job_execution_id = (SELECT MAX(job_execution_id) FROM batch_job_execution);

DELETE FROM settlements;
COMMIT ;

