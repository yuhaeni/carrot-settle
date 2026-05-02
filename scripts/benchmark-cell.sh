#!/usr/bin/env bash
#
# benchmark-cell.sh — 정산 배치 chunk size 벤치마크 1 cell 자동 측정
#
# 1 cell = (chunk size × data count) 한 조합. 다음을 수행:
#   1. settlements + Spring Batch 메타테이블 reset
#   2. N 건 시드 INSERT
#   3. POST /api/v1/settlements/calculate 트리거
#   4. GET /api/v1/admin/batch-jobs/executions/{id} 로 메트릭 조회
#   5. 결과 표용 markdown row 한 줄을 stdout 에 출력 (진행 로그는 stderr)
#
# 사용법:
#   ./scripts/benchmark-cell.sh <chunk_size_label> <data_count> [target_date]
#
# 예시:
#   ./scripts/benchmark-cell.sh 100 1000
#   ./scripts/benchmark-cell.sh 10 10000 2026-01-03
#
# 주의: 본 스크립트는 application.yaml 의 chunk-size 를 변경하지 않는다.
# 호출 전에 직접 yaml 편집 + bootRun cold restart 필요. chunk_size_label 인자는
# 출력 markdown row 의 라벨링용 (실제 yaml 값과 일치해야 의미 있음).
#
# 환경 변수 override:
#   API_BASE       (기본: http://localhost:8080)
#   DB_CONTAINER   (기본: carrot-settle-postgres-1)
#   DB_USER        (기본: carrot)
#   DB_NAME        (기본: carrot_settle)
#   SELLER_EMAIL   (기본: chulsoo@example.com — V3 seed STANDARD 셀러)
#

set -euo pipefail

# ---- args ----
if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <chunk_size_label> <data_count> [target_date]" >&2
  echo "Example: $0 100 1000 2026-01-02" >&2
  exit 1
fi

CHUNK_LABEL="$1"
DATA_COUNT="$2"
TARGET_DATE="${3:-2026-01-02}"

# ---- env defaults ----
API_BASE="${API_BASE:-http://localhost:8080}"
DB_CONTAINER="${DB_CONTAINER:-carrot-settle-postgres-1}"
DB_USER="${DB_USER:-carrot}"
DB_NAME="${DB_NAME:-carrot_settle}"
SELLER_EMAIL="${SELLER_EMAIL:-chulsoo@example.com}"

# ---- prerequisites ----
command -v jq >/dev/null 2>&1 || { echo "ERROR: jq not installed" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "ERROR: curl not installed" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "ERROR: docker not installed" >&2; exit 1; }

psql_exec() {
  docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc "$1"
}

format_count() {
  # 1000 → "1,000". pure bash — macOS BSD sed / GNU sed / locale 무관.
  # printf "%'d" 는 LC_NUMERIC 의존, sed `:a;...;ta` 는 macOS sed 미지원이라 회피.
  local n="$1"
  local result=""
  while [ ${#n} -gt 3 ]; do
    result=",${n: -3}$result"
    n=${n:0:${#n}-3}
  done
  printf "%s%s\n" "$n" "$result"
}

# ---- 1. reset ----
echo "[1/5] Reset settlements + batch metadata..." >&2
psql_exec "
TRUNCATE batch_step_execution_context, batch_job_execution_context,
         batch_step_execution, batch_job_execution_params, batch_job_execution,
         batch_job_instance RESTART IDENTITY CASCADE;
DELETE FROM settlements;
" >/dev/null

# ---- 2. seed ----
echo "[2/5] Seed $(format_count "$DATA_COUNT") records (settlement_date < $TARGET_DATE)..." >&2
psql_exec "
INSERT INTO settlements (
    seller_id, settlement_date, status, total_amount,
    pg_fee, platform_fee, total_fee, net_amount,
    skip_count, version, created_at, updated_at
)
SELECT
    (SELECT id FROM sellers WHERE email = '$SELLER_EMAIL'),
    DATE '$TARGET_DATE' - 1,
    'INCOMPLETED',
    10000.00, 300.00, 500.00, 800.00, 9200.00,
    0, 0, NOW(), NOW()
FROM generate_series(1, $DATA_COUNT);
" >/dev/null

# ---- 3. trigger ----
echo "[3/5] Trigger batch via POST /calculate (targetDate=$TARGET_DATE)..." >&2
TRIGGER_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/settlements/calculate" \
  -H "Content-Type: application/json" \
  -d "{\"targetDate\":\"$TARGET_DATE\"}")
JOB_ID=$(echo "$TRIGGER_RESPONSE" | jq -r '.jobExecutionId // empty')

if [ -z "$JOB_ID" ]; then
  echo "ERROR: Failed to trigger batch. Response:" >&2
  echo "$TRIGGER_RESPONSE" >&2
  exit 2
fi

# ---- 4. fetch metrics ----
echo "[4/5] Fetch metrics from admin endpoint (jobExecutionId=$JOB_ID)..." >&2
METRICS=$(curl -sS "$API_BASE/api/v1/admin/batch-jobs/executions/$JOB_ID")

STATUS=$(echo "$METRICS" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS" != "COMPLETED" ]; then
  echo "ERROR: Batch did not complete (status=$STATUS)" >&2
  echo "$METRICS" | jq . >&2
  exit 3
fi

READ_COUNT=$(echo "$METRICS" | jq -r '.readCount')
WRITE_COUNT=$(echo "$METRICS" | jq -r '.writeCount')
COMMIT_COUNT=$(echo "$METRICS" | jq -r '.commitCount')
if [ "$READ_COUNT" != "$DATA_COUNT" ] || [ "$WRITE_COUNT" != "$DATA_COUNT" ]; then
  echo "WARNING: count mismatch — expected $DATA_COUNT, got read=$READ_COUNT write=$WRITE_COUNT" >&2
fi

# chunk size label vs 실제 yaml 값 일치 여부 검증.
# read_count / commit_count = 실제 chunk size (대략, 마지막 chunk 가 가득 안 찼을 수도 있음).
# 라벨 미일치 시 경고 — yaml 편집 누락 / bootRun cold restart 누락 의심.
if [ "$COMMIT_COUNT" -gt 0 ]; then
  EFFECTIVE_CHUNK=$((READ_COUNT / COMMIT_COUNT))
  # 라벨이 숫자일 때만 비교. 라벨이 "10" / "100" / "500" 가 아닌 경우 (e.g. 임시 라벨) 검증 skip.
  if [[ "$CHUNK_LABEL" =~ ^[0-9]+$ ]]; then
    # 마지막 chunk 가 partial 이라 실제와 1~2 차이 날 수 있음. 50% 이상 벗어나면 명백한 불일치.
    HALF=$((CHUNK_LABEL / 2))
    if [ "$EFFECTIVE_CHUNK" -lt "$HALF" ] || [ "$EFFECTIVE_CHUNK" -gt "$((CHUNK_LABEL * 2))" ]; then
      echo "WARNING: chunk size mismatch — label=$CHUNK_LABEL but effective=$EFFECTIVE_CHUNK (read/commit=$READ_COUNT/$COMMIT_COUNT)" >&2
      echo "  → application.yaml 편집 후 bootRun cold restart 했는지 확인" >&2
    fi
  fi
fi

# ---- 5. output markdown row ----
echo "[5/5] Done. Markdown row → stdout (paste into docs)" >&2
DATA_FMT=$(format_count "$DATA_COUNT")
echo "$METRICS" | jq -r --arg data "$DATA_FMT" --arg chunk "$CHUNK_LABEL" '
  "| \($data) | \($chunk) | \(.elapsedMs) | TBD | \(.impliedPerChunkMs) | TBD× | commit \(.commitCount)회 — read/write \(.readCount)/\(.writeCount) |"
'
