package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 배치에서 skip된 항목을 알리는 도메인 이벤트.
 *
 * <p>현재는 in-process {@link org.springframework.context.ApplicationEventPublisher}로 발행되며,
 * M7(11~12주차)에서 동일 이벤트를 Kafka 토픽으로 교체할 예정이다. Processor / SkipListener 코드는
 * 그 시점에도 변경되지 않는다.
 */
public record SettlementSkippedEvent(
    Long settlementId,
    SettlementStatus status,
    BigDecimal netAmount,
    String reason,
    LocalDateTime occurredAt) {}
