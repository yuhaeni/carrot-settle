package com.haeni.carrot.settle.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.haeni.carrot.settle.TestcontainersConfiguration;
import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.common.exception.ErrorCode;
import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementBatchRestartTest {

  @Autowired private JobOperator jobOperator;
  @Autowired private Job settlementJob;
  @Autowired private SettlementBatchService settlementBatchService;
  @Autowired private SettlementRepository settlementRepository;

  @MockitoSpyBean private SettlementItemProcessor settlementItemProcessor;

  @PersistenceContext private EntityManager em;

  private Seller standardSeller;

  @BeforeEach
  void setUp() {
    settlementRepository.deleteAllInBatch();
    Mockito.reset(settlementItemProcessor);
    standardSeller =
        em.createQuery("SELECT s FROM Seller s WHERE s.email = :email", Seller.class)
            .setParameter("email", "chulsoo@example.com")
            .getSingleResult();
  }

  @Test
  @DisplayName("chunk 처리 중 RuntimeException이 발생하면 JobExecution이 FAILED + 데이터 변경 없음")
  void chunk_실패_시_FAILED_및_롤백() throws Exception {
    LocalDate target = LocalDate.of(2026, 3, 1);
    LocalDate past = target.minusDays(1);
    settlementRepository.saveAll(
        List.of(newSettlement(standardSeller, past), newSettlement(standardSeller, past)));

    Mockito.doThrow(new RuntimeException("의도적 실패"))
        .when(settlementItemProcessor)
        .process(any());

    JobExecution exec = jobOperator.start(settlementJob, paramsFor(target));

    assertThat(exec.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(settlementRepository.findAll())
        .allMatch(s -> s.getStatus() == SettlementStatus.INCOMPLETED);
  }

  @Test
  @DisplayName("실패한 동일 JobParameter 재실행 시 같은 JobInstance에서 처리가 완료된다")
  void 실패_후_재실행_시_정상_처리() throws Exception {
    LocalDate target = LocalDate.of(2026, 3, 2);
    LocalDate past = target.minusDays(1);
    settlementRepository.saveAll(
        List.of(newSettlement(standardSeller, past), newSettlement(standardSeller, past)));

    JobParameters params = paramsFor(target);

    Mockito.doThrow(new RuntimeException("첫 실행 실패"))
        .when(settlementItemProcessor)
        .process(any());
    JobExecution failed = jobOperator.start(settlementJob, params);
    assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);

    Mockito.doCallRealMethod().when(settlementItemProcessor).process(any());
    JobExecution rerun = jobOperator.start(settlementJob, params);

    assertThat(rerun.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(rerun.getJobInstance().getInstanceId())
        .isEqualTo(failed.getJobInstance().getInstanceId());
    assertThat(settlementRepository.findAll())
        .allMatch(s -> s.getStatus() == SettlementStatus.COMPLETED);
  }

  @Test
  @DisplayName("이미 성공한 JobParameter로 재호출 시 BATCH_ALREADY_COMPLETED를 반환한다")
  void 동일_JobParameter_재실행_시_중복_방지() throws Exception {
    LocalDate target = LocalDate.of(2026, 3, 3);
    LocalDate past = target.minusDays(1);
    settlementRepository.save(newSettlement(standardSeller, past));

    settlementBatchService.runSettlementJob(target);

    assertThatThrownBy(() -> settlementBatchService.runSettlementJob(target))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.BATCH_ALREADY_COMPLETED);
  }

  @Test
  @DisplayName("JobOperator를 직접 사용하면 raw JobInstanceAlreadyCompleteException이 노출된다")
  void 동일_JobParameter_재실행_시_raw_예외() throws Exception {
    LocalDate target = LocalDate.of(2026, 3, 4);
    LocalDate past = target.minusDays(1);
    settlementRepository.save(newSettlement(standardSeller, past));

    JobParameters params = paramsFor(target);
    jobOperator.start(settlementJob, params);

    assertThatThrownBy(() -> jobOperator.start(settlementJob, params))
        .isInstanceOf(JobInstanceAlreadyCompleteException.class);
  }

  private Settlement newSettlement(Seller seller, LocalDate date) {
    BigDecimal totalAmount = new BigDecimal("10000");
    FeeDetail fee = new FeeDetail(new BigDecimal("300"), new BigDecimal("500"));
    BigDecimal netAmount = totalAmount.subtract(fee.getTotalFee());
    return new Settlement(seller, date, totalAmount, fee, netAmount);
  }

  private JobParameters paramsFor(LocalDate target) {
    return new JobParametersBuilder()
        .addLocalDate(SettlementBatchConfig.PARAM_TARGET_DATE, target)
        .toJobParameters();
  }
}
