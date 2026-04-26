package com.haeni.carrot.settle.infrastructure.settlement;

import com.haeni.carrot.settle.domain.settlement.SettlementSkipLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementSkipLogRepository extends JpaRepository<SettlementSkipLog, Long> {}
