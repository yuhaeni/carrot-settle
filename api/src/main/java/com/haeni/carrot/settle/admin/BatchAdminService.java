package com.haeni.carrot.settle.admin;

import com.haeni.carrot.settle.admin.dto.BatchExecutionResponseDto;
import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchAdminService {

  private final JobRepository jobRepository;

  public BatchExecutionResponseDto getJobExecution(Long jobExecutionId) {
    // JobRepository.getJobExecution() 의 nullable 계약과 달리 실제 구현체(SimpleJobExplorer)가
    // EmptyResultDataAccessException 을 던진다. null 체크 + 예외 catch 둘 다 방어.
    JobExecution execution;
    try {
      execution = jobRepository.getJobExecution(jobExecutionId);
    } catch (EmptyResultDataAccessException e) {
      throw new BusinessException(ErrorCode.BATCH_JOB_EXECUTION_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    if (execution == null) {
      throw new BusinessException(ErrorCode.BATCH_JOB_EXECUTION_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    return BatchExecutionResponseDto.from(execution);
  }
}
