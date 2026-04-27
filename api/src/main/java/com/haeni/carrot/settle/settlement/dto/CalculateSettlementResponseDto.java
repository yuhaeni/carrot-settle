package com.haeni.carrot.settle.settlement.dto;

import org.springframework.batch.core.job.JobExecution;

public record CalculateSettlementResponseDto(
    Long jobExecutionId, String status, String exitCode) {

  public static CalculateSettlementResponseDto from(JobExecution execution) {
    return new CalculateSettlementResponseDto(
        execution.getId(),
        execution.getStatus().name(),
        execution.getExitStatus().getExitCode());
  }
}