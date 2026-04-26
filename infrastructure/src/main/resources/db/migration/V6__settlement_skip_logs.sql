-- =============================================
-- V6: Settlement skip 로그 테이블
-- 정산 배치에서 fault-tolerant skip된 항목(예: 음수 정산금)을 별도 채널로 추적
-- ApplicationEvents 기반 (M7에서 Kafka 토픽으로 교체 예정)
-- =============================================

CREATE TABLE settlement_skip_logs (
    id BIGSERIAL PRIMARY KEY,
    settlement_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    net_amount NUMERIC(19, 2) NOT NULL,
    reason TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_skip_log_settlement_id ON settlement_skip_logs (settlement_id);
CREATE INDEX idx_skip_log_occurred_at ON settlement_skip_logs (occurred_at);
