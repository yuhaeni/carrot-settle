package com.haeni.carrot.settle.admin.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

public record BatchExecutionResponseDto(
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

  public static BatchExecutionResponseDto from(JobExecution execution) {
    StepExecution step = execution.getStepExecutions().stream().findFirst().orElse(null);
    if (step == null) {
      return new BatchExecutionResponseDto(
          execution.getId(),
          execution.getJobInstance().getJobName(),
          null,
          execution.getStatus().name(),
          execution.getExitStatus().getExitCode(),
          null,
          null,
          null,
          null,
          null);
    }

    Long elapsedMs = computeElapsedMs(step.getStartTime(), step.getEndTime());
    long commitCount = step.getCommitCount();
    Long impliedPerChunkMs = (elapsedMs != null && commitCount > 0) ? elapsedMs / commitCount : null;

    return new BatchExecutionResponseDto(
        execution.getId(),
        execution.getJobInstance().getJobName(),
        step.getStepName(),
        step.getStatus().name(),
        step.getExitStatus().getExitCode(),
        step.getReadCount(),
        step.getWriteCount(),
        commitCount,
        elapsedMs,
        impliedPerChunkMs);
  }

  private static Long computeElapsedMs(LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null) {
      return null;
    }
    return Duration.between(start, end).toMillis();
  }
}
