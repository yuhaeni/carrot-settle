package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class SettlementItemProcessor implements ItemProcessor<Settlement, Settlement> {

  private static final Logger log = LoggerFactory.getLogger(SettlementItemProcessor.class);

  @Override
  public Settlement process(Settlement item) {
    if (item.getStatus() != SettlementStatus.INCOMPLETED) {
      log.warn(
          "이미 처리된 정산이라 스킵합니다. settlementId={}, status={}, netAmount={}",
          item.getId(),
          item.getStatus(),
          item.getNetAmount());
      return null;
    }

    if (item.getNetAmount().signum() < 0) {
      log.error(
          "음수 정산금이 발견되어 스킵 처리합니다. settlementId={}, status={}, netAmount={}",
          item.getId(),
          item.getStatus(),
          item.getNetAmount());
      throw new IllegalStateException(
          "음수 정산금: settlementId=" + item.getId() + ", netAmount=" + item.getNetAmount());
    }

    item.complete();
    return item;
  }
}
