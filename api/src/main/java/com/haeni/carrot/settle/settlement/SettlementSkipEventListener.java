package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.SettlementSkipLog;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementSkipLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SettlementSkippedEvent}를 수신해 {@code settlement_skip_logs} 테이블에 적재한다.
 *
 * <p>Spring Batch chunk 트랜잭션 영향을 받지 않도록 {@link Propagation#REQUIRES_NEW}로 별도
 * 트랜잭션을 시작한다. M7에서 Kafka 도입 시 본 클래스를 KafkaProducer 호출로 교체하면 된다.
 */
@Component
@RequiredArgsConstructor
public class SettlementSkipEventListener {

  private final SettlementSkipLogRepository skipLogRepository;

  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(SettlementSkippedEvent event) {
    skipLogRepository.save(
        new SettlementSkipLog(
            event.settlementId(),
            event.status().name(),
            event.netAmount(),
            event.reason(),
            event.occurredAt()));
  }
}
