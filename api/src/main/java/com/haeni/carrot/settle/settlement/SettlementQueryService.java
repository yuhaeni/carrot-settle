package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import com.haeni.carrot.settle.settlement.dto.SettlementResponseDto;
import com.haeni.carrot.settle.settlement.dto.SettlementSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {

  private final SettlementRepository settlementRepository;

  public Page<SettlementResponseDto> search(
      SettlementSearchCondition condition, Pageable pageable) {
    return settlementRepository
        .search(
            condition.sellerId(),
            condition.startDate(),
            condition.endDate(),
            condition.status(),
            pageable)
        .map(SettlementResponseDto::from);
  }
}
