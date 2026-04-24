package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.common.exception.ErrorCode;
import com.haeni.carrot.settle.settlement.dto.CalculateSettlementResponseDto;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementBatchService {

  private final JobLauncher jobLauncher;
  private final Job settlementJob;

  public CalculateSettlementResponseDto runSettlementJob(LocalDate targetDate) {
    try {
      JobParameters parameters =
          new JobParametersBuilder()
              .addLocalDate(SettlementBatchConfig.PARAM_TARGET_DATE, targetDate)
              .toJobParameters();

      JobExecution execution = jobLauncher.run(settlementJob, parameters);
      return CalculateSettlementResponseDto.from(execution);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.BATCH_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}