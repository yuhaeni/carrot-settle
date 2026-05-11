package com.haeni.carrot.settle.settlement.dto;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementResponseDto(
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

  public static SettlementResponseDto from(Settlement settlement) {
    return new SettlementResponseDto(
        settlement.getId(),
        settlement.getSeller().getId(),
        settlement.getSettlementDate(),
        settlement.getStatus(),
        toLong(settlement.getTotalAmount()),
        toLong(settlement.getFeeDetail().getPgFee()),
        toLong(settlement.getFeeDetail().getPlatformFee()),
        toLong(settlement.getFeeDetail().getTotalFee()),
        toLong(settlement.getNetAmount()),
        settlement.getCreatedAt());
  }

  private static Long toLong(java.math.BigDecimal value) {
    return value.setScale(0, RoundingMode.HALF_UP).longValue();
  }
}