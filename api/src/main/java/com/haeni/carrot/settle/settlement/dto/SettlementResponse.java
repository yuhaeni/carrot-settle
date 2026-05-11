package com.haeni.carrot.settle.settlement.dto;

import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementResponse(
    Long id,
    Long sellerId,
    LocalDate settlementDate,
    SettlementStatus status,
    Long totalAmount,
    Long pgFee,
    Long platformFee,
    Long totalFee,
    Long netAmount,
    LocalDateTime createdAt) {

  public static SettlementResponse from(SettlementResponseDto dto) {
    return new SettlementResponse(
        dto.id(),
        dto.sellerId(),
        dto.settlementDate(),
        dto.status(),
        dto.totalAmount(),
        dto.pgFee(),
        dto.platformFee(),
        dto.totalFee(),
        dto.netAmount(),
        dto.createdAt());
  }
}