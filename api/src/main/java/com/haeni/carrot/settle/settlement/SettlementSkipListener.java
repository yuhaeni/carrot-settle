package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring Batch chunk가 fault-tolerant skip 처리한 항목을 잡아 {@link SettlementSkippedEvent}를
 * 발행한다.
 *
 * <p>Processor에서 직접 publish하면 chunk rollback에 함께 휩쓸려 이벤트가 소실되므로, chunk skip
 * 단계에서 호출되는 {@link SkipListener#onSkipInProcess}에서 발행한다.
 */
@Component
@RequiredArgsConstructor
public class SettlementSkipListener implements SkipListener<Settlement, Settlement> {

  private static final Logger log = LoggerFactory.getLogger(SettlementSkipListener.class);

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void onSkipInProcess(Settlement item, Throwable t) {
    log.warn(
        "Settlement skip 이벤트 발행. settlementId={}, status={}, netAmount={}, reason={}",
        item.getId(),
        item.getStatus(),
        item.getNetAmount(),
        t.getMessage());

    eventPublisher.publishEvent(
        new SettlementSkippedEvent(
            item.getId(),
            item.getStatus(),
            item.getNetAmount(),
            t.getMessage(),
            LocalDateTime.now()));
  }
}