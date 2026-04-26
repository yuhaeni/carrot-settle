package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SettlementBatchConfig {

  public static final String JOB_NAME = "settlementJob";
  public static final String STEP_NAME = "settlementStep";
  public static final String PARAM_TARGET_DATE = "targetDate";
  public static final int CHUNK_SIZE = 100;
  public static final int SKIP_LIMIT = 100;

  @Bean
  public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
    return new JobBuilder(JOB_NAME, jobRepository).start(settlementStep).build();
  }

  @Bean
  public Step settlementStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      JpaPagingItemReader<Settlement> settlementReader,
      SettlementItemProcessor settlementItemProcessor,
      ItemWriter<Settlement> settlementWriter,
      SettlementSkipListener settlementSkipListener) {
    return new StepBuilder(STEP_NAME, jobRepository)
        .<Settlement, Settlement>chunk(CHUNK_SIZE)
        .transactionManager(transactionManager)
        .reader(settlementReader)
        .processor(settlementItemProcessor)
        .writer(settlementWriter)
        .faultTolerant()
        .skip(IllegalStateException.class)
        .skipLimit(SKIP_LIMIT) // TODO skipLimit 100이 적절한지만 한 번 확인
        .listener(settlementSkipListener)
        .build();
  }

  @Bean
  @StepScope
  public JpaPagingItemReader<Settlement> settlementReader(
      @Value("#{jobParameters['" + PARAM_TARGET_DATE + "']}") LocalDate targetDate,
      EntityManagerFactory entityManagerFactory) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("status", SettlementStatus.INCOMPLETED);
    parameters.put("targetDate", targetDate);

    return new JpaPagingItemReaderBuilder<Settlement>()
        .name("settlementReader")
        .entityManagerFactory(entityManagerFactory)
        .queryString(
            "SELECT s FROM Settlement s "
                + "WHERE s.status = :status AND s.settlementDate < :targetDate "
                + "ORDER BY s.id")
        .parameterValues(parameters)
        .pageSize(CHUNK_SIZE)
        .build();
  }

  /**
   * chunk 단위로 merge → flush → clear를 수행한다. em.clear()로 영속성 컨텍스트를 초기화하여 대용량 배치 OOM을 방지한다. (PRD 4.4
   * 기술 포인트)
   */
  @Bean
  public ItemWriter<Settlement> settlementWriter(EntityManager entityManager) {
    return chunk -> {
      chunk.getItems().forEach(entityManager::merge);
      entityManager.flush();
      entityManager.clear();
    };
  }
}
