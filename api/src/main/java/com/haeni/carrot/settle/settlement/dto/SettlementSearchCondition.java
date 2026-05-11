package com.haeni.carrot.settle.settlement.dto;

import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import java.time.LocalDate;

public record SettlementSearchCondition(
    Long sellerId, LocalDate startDate, LocalDate endDate, SettlementStatus status) {}
