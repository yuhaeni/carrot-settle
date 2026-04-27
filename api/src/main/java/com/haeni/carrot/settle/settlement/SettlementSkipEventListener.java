package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.SettlementSkipLog;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementSkipLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SettlementSkippedEvent}를 수신해 후속 처리를 수행한다.
 *
 * <ol>
 *   <li>Settlement {@code skipCount} 증가
 *   <li>임계치(설정값 {@code settle.batch.skip-block-threshold}) 도달 시 {@code BLOCKED} 전이 — 다음 배치
 *       Reader 쿼리에서 자동 제외
 *   <li>{@code settlement_skip_logs} 테이블에 적재
 * </ol>
 *
 * <p>Spring Batch chunk 트랜잭션 영향을 받지 않도록 {@link Propagation#REQUIRES_NEW}로 별도 트랜잭션을
 * 시작한다. M7에서 Kafka 도입 시 본 클래스를 KafkaProducer 호출로 교체하면 된다.
 */
@Component
@RequiredArgsConstructor
public class SettlementSkipEventListener {

  private static final Logger log = LoggerFactory.getLogger(SettlementSkipEventListener.class);

  private final SettlementSkipLogRepository skipLogRepository;
  private final SettlementRepository settlementRepository;

  @Value("${settle.batch.skip-block-threshold:3}")
  private int skipBlockThreshold;

  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(SettlementSkippedEvent event) {
    settlementRepository
        .findById(event.settlementId())
        .ifPresent(
            settlement -> {
              settlement.incrementSkipCount();
              if (settlement.getSkipCount() >= skipBlockThreshold) {
                settlement.block();
                log.warn(
                    "Settlement skip 임계치 초과로 BLOCKED 전이. settlementId={}, skipCount={}, threshold={}",
                    settlement.getId(),
                    settlement.getSkipCount(),
                    skipBlockThreshold);
              }
            });

    skipLogRepository.save(
        new SettlementSkipLog(
            event.settlementId(),
            event.status().name(),
            event.netAmount(),
            event.reason(),
            event.occurredAt()));
  }
}
