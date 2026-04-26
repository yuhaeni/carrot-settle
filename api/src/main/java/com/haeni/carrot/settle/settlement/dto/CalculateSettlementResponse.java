package com.haeni.carrot.settle.settlement.dto;

public record CalculateSettlementResponse(Long jobExecutionId, String status, String exitCode) {

  public static CalculateSettlementResponse from(CalculateSettlementResponseDto dto) {
    return new CalculateSettlementResponse(dto.jobExecutionId(), dto.status(), dto.exitCode());
  }
}