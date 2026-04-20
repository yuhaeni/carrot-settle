package com.haeni.carrot.settle.infrastructure.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {}