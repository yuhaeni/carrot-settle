package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class SettlementItemProcessor implements ItemProcessor<Settlement, Settlement> {

  @Override
  public Settlement process(Settlement item) {
    item.complete();
    return item;
  }
}
