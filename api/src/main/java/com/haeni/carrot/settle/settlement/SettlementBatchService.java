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
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementBatchService {

  private final JobOperator jobOperator;
  private final Job settlementJob;

  public CalculateSettlementResponseDto runSettlementJob(LocalDate targetDate) {
    JobParameters parameters =
        new JobParametersBuilder()
            .addLocalDate(SettlementBatchConfig.PARAM_TARGET_DATE, targetDate)
            .toJobParameters();

    try {
      JobExecution execution = jobOperator.start(settlementJob, parameters);
      return CalculateSettlementResponseDto.from(execution);
    } catch (JobInstanceAlreadyCompleteException e) {
      throw new BusinessException(ErrorCode.BATCH_ALREADY_COMPLETED, HttpStatus.CONFLICT);
    } catch (JobExecutionAlreadyRunningException e) {
      throw new BusinessException(ErrorCode.BATCH_ALREADY_RUNNING, HttpStatus.CONFLICT);
    } catch (Exception e) {
      throw new BusinessException(
          ErrorCode.BATCH_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
