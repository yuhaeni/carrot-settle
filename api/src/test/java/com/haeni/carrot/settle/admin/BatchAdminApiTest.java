package com.haeni.carrot.settle.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haeni.carrot.settle.TestcontainersConfiguration;
import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import com.haeni.carrot.settle.settlement.SettlementBatchConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `GET /api/v1/admin/batch-jobs/executions/{id}` HTTP 응답 검증. 측정용 generic admin endpoint 가
 * `JobRepository`로 메타테이블을 읽어 step 메트릭(read/write/commit count, elapsedMs, impliedPerChunkMs)을
 * 응답으로 노출하는지 확인.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "settle.batch.chunk-size=10")
class BatchAdminApiTest {

  @TestConfiguration
  static class BatchTestConfig {
    @Bean
    JobOperatorTestUtils jobOperatorTestUtils(
        JobOperator jobOperator, JobRepository jobRepository, Job settlementJob) {
      JobOperatorTestUtils utils = new JobOperatorTestUtils(jobOperator, jobRepository);
      utils.setJob(settlementJob);
      return utils;
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private SettlementRepository settlementRepository;
  @Autowired private JobOperatorTestUtils jobOperatorTestUtils;

  @PersistenceContext private EntityManager em;

  private Seller standardSeller;

  @BeforeEach
  void setUp() {
    settlementRepository.deleteAllInBatch();
    standardSeller =
        em.createQuery("SELECT s FROM Seller s WHERE s.email = :email", Seller.class)
            .setParameter("email", "chulsoo@example.com")
            .getSingleResult();
  }

  @Test
  @DisplayName("배치 실행 후 admin endpoint로 메트릭을 조회하면 step 카운트와 elapsedMs가 노출된다")
  void getJobExecution_returnsStepMetrics() throws Exception {
    // given — chunk 10 × 30건 → 3 chunk
    LocalDate target = LocalDate.of(2026, 5, 1);
    LocalDate past = target.minusDays(1);
    List<Settlement> seeds =
        IntStream.range(0, 30).mapToObj(i -> newSettlement(standardSeller, past)).toList();
    settlementRepository.saveAll(seeds);

    JobExecution execution = jobOperatorTestUtils.startJob(paramsFor(target));
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

    // when + then — admin endpoint 응답 검증
    mockMvc
        .perform(get("/api/v1/admin/batch-jobs/executions/{id}", execution.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobExecutionId").value(execution.getId()))
        .andExpect(jsonPath("$.jobName").value("settlementJob"))
        .andExpect(jsonPath("$.stepName").value("settlementStep"))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.exitCode").value("COMPLETED"))
        .andExpect(jsonPath("$.readCount").value(30))
        .andExpect(jsonPath("$.writeCount").value(30))
        .andExpect(jsonPath("$.commitCount").value(Matchers.greaterThan(0)))
        .andExpect(jsonPath("$.elapsedMs").value(Matchers.greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.impliedPerChunkMs").value(Matchers.greaterThanOrEqualTo(0)));
  }

  @Test
  @DisplayName("존재하지 않는 jobExecutionId 조회 시 404 + BATCH_JOB_EXECUTION_NOT_FOUND 응답")
  void getJobExecution_notFound() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/batch-jobs/executions/{id}", 999_999_999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BATCH_JOB_EXECUTION_NOT_FOUND"));
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
        .addLong("runAt", System.nanoTime())
        .toJobParameters();
  }
}