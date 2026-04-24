package com.haeni.carrot.settle.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.TestcontainersConfiguration;
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
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@SpringBatchTest
@Import(TestcontainersConfiguration.class)
class SettlementBatchIntegrationTest {

  @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
  @Autowired private SettlementRepository settlementRepository;

  @PersistenceContext private EntityManager em;

  private Seller standardSeller;
  private Seller vipSeller;

  @BeforeEach
  void setUp() {
    settlementRepository.deleteAllInBatch();
    standardSeller =
        em.createQuery("SELECT s FROM Seller s WHERE s.email = :email", Seller.class)
            .setParameter("email", "chulsoo@example.com")
            .getSingleResult();
    vipSeller =
        em.createQuery("SELECT s FROM Seller s WHERE s.email = :email", Seller.class)
            .setParameter("email", "minjun@example.com")
            .getSingleResult();
  }

  @Test
  @DisplayName("기준일 이전 INCOMPLETED Settlement가 COMPLETED로 전이된다")
  void batch_INCOMPLETED_to_COMPLETED() throws Exception {
    // given
    LocalDate target = LocalDate.of(2026, 4, 24);
    LocalDate past = target.minusDays(1);

    settlementRepository.saveAll(
        List.of(
            newSettlement(standardSeller, past),
            newSettlement(standardSeller, past),
            newSettlement(vipSeller, past)));

    // when
    JobExecution execution = jobLauncherTestUtils.launchJob(paramsFor(target));

    // then
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(execution.getStepExecutions()).hasSize(1);
    assertThat(execution.getStepExecutions().iterator().next().getWriteCount()).isEqualTo(3);

    List<Settlement> all = settlementRepository.findAll();
    assertThat(all).hasSize(3);
    assertThat(all).allMatch(s -> s.getStatus() == SettlementStatus.COMPLETED);
  }

  @Test
  @DisplayName("기준일 당일 및 이후 Settlement는 배치 대상에서 제외된다")
  void batch_filter_by_target_date() throws Exception {
    // given — 기준일 기준으로 '이전'만 대상 (AC-2)
    LocalDate target = LocalDate.of(2026, 4, 24);

    Settlement past = newSettlement(standardSeller, target.minusDays(1));
    Settlement onTarget = newSettlement(standardSeller, target); // 기준일 당일은 < 조건으로 제외
    Settlement future = newSettlement(standardSeller, target.plusDays(1));
    settlementRepository.saveAll(List.of(past, onTarget, future));

    // when
    jobLauncherTestUtils.launchJob(paramsFor(target));

    // then
    Map<LocalDate, SettlementStatus> byDate =
        settlementRepository.findAll().stream()
            .collect(Collectors.toMap(Settlement::getSettlementDate, Settlement::getStatus));

    assertThat(byDate.get(target.minusDays(1))).isEqualTo(SettlementStatus.COMPLETED);
    assertThat(byDate.get(target)).isEqualTo(SettlementStatus.INCOMPLETED);
    assertThat(byDate.get(target.plusDays(1))).isEqualTo(SettlementStatus.INCOMPLETED);
  }

  @Test
  @DisplayName("이미 COMPLETED인 Settlement는 재실행에서 제외되어 중복 처리가 없다 (AC-3 멱등성)")
  void batch_idempotent_on_rerun() throws Exception {
    // given — 첫 배치로 모두 COMPLETED 전이
    LocalDate target = LocalDate.of(2026, 4, 24);
    LocalDate past = target.minusDays(1);
    settlementRepository.saveAll(
        List.of(newSettlement(standardSeller, past), newSettlement(vipSeller, past)));

    jobLauncherTestUtils.launchJob(paramsFor(target));

    // 새로 INCOMPLETED 하나 추가
    settlementRepository.save(newSettlement(standardSeller, past));

    // when — 동일 기준일로 재실행 (JobParameter에 timestamp 추가로 새 instance)
    JobExecution rerun = jobLauncherTestUtils.launchJob(paramsFor(target));

    // then — 새로 추가된 1건만 처리되고 기존 2건은 이미 COMPLETED라 Reader가 스킵
    assertThat(rerun.getStepExecutions().iterator().next().getWriteCount()).isEqualTo(1);
    List<Settlement> all = settlementRepository.findAll();
    assertThat(all).hasSize(3);
    assertThat(all).allMatch(s -> s.getStatus() == SettlementStatus.COMPLETED);
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
