package com.haeni.carrot.settle.admin.dto;

public record BatchExecutionResponse(
    Long jobExecutionId,
    String jobName,
    String stepName,
    String status,
    String exitCode,
    Long readCount,
    Long writeCount,
    Long commitCount,
    Long elapsedMs,
    Long impliedPerChunkMs) {

  public static BatchExecutionResponse from(BatchExecutionResponseDto dto) {
    return new BatchExecutionResponse(
        dto.jobExecutionId(),
        dto.jobName(),
        dto.stepName(),
        dto.status(),
        dto.exitCode(),
        dto.readCount(),
        dto.writeCount(),
        dto.commitCount(),
        dto.elapsedMs(),
        dto.impliedPerChunkMs());
  }
}
