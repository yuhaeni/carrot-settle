package com.haeni.carrot.settle.infrastructure.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementRepositoryCustom {

  Page<Settlement> search(
      Long sellerId,
      LocalDate startDate,
      LocalDate endDate,
      SettlementStatus status,
      Pageable pageable);
}
