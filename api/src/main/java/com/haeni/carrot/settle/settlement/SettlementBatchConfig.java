package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class SettlementBatchConfig {

  public static final String JOB_NAME = "settlementJob";
  public static final String STEP_NAME = "settlementStep";
  public static final String PARAM_TARGET_DATE = "targetDate";

  @Value("${settle.batch.chunk-size:100}")
  private int chunkSize;

  @Value("${settle.batch.skip-limit:10}")
  private int skipLimit;

  @Value("${settle.batch.skip-block-threshold:3}")
  private int skipBlockThreshold;

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
        .<Settlement, Settlement>chunk(chunkSize)
        .transactionManager(transactionManager)
        .reader(settlementReader)
        .processor(settlementItemProcessor)
        .writer(settlementWriter)
        .faultTolerant()
        .skip(SettlementSkippableException.class)
        .skipLimit(skipLimit)
        .listener(settlementSkipListener)
        .build();
  }

  /**
   * cursor 기반 페이징. JPQL의 {@code id > :lastId ORDER BY id}로 OFFSET 누적을 회피한다. {@code getPage()}를 0으로
   * 고정 + {@code read()} 호출마다 lastId를 갱신하여 state-mutating filter(INCOMPLETED → COMPLETED) 환경에서도 row 누락
   * 없이 다중 chunk 처리. 멱등 restart는 filter {@code status=INCOMPLETED}가 처리된 row를 자동 제외해주므로 별도 보장 불필요
   * (saveState는 default true 유지).
   *
   * <p><b>도메인 가정</b> — 두 가정 중 하나라도 깨지면 cursor 전략 재설계 필요:
   *
   * <ol>
   *   <li><b>Settlement.id 단조 증가</b> — PostgreSQL {@code bigserial}로 충족. id 재사용/롤백/수동 삽입이 발생하면 cursor가
   *       처리한 영역을 다시 만나거나 중간 row를 건너뜀.
   *   <li><b>자정 cutoff batch</b> — {@code settlementDate < targetDate} 필터로 batch 도중 새 row(오늘 confirm된 주문)는
   *       대상에서 자동 제외됨. 만약 진행 중인 day의 INCOMPLETED를 처리하는 시나리오로 변경되면 cursor가 이미 지나간 id
   *       구간에 새 row가 끼어들어 누락 발생 가능.
   * </ol>
   */
  @Bean
  @StepScope
  public JpaPagingItemReader<Settlement> settlementReader(
      @Value("#{jobParameters['" + PARAM_TARGET_DATE + "']}") LocalDate targetDate,
      EntityManagerFactory entityManagerFactory) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("status", SettlementStatus.INCOMPLETED);
    parameters.put("targetDate", targetDate);
    parameters.put("skipThreshold", skipBlockThreshold);

    SettlementCursorQueryProvider queryProvider = new SettlementCursorQueryProvider();

    JpaPagingItemReader<Settlement> reader =
        new JpaPagingItemReader<Settlement>(entityManagerFactory) {
          private long lastIdSeen = 0L;

          // page counter 자동 증가를 막아 OFFSET=0 고정. cursor(id > :lastId)가 위치를 책임지므로
          // OFFSET이 누적되면 lastId 이후 (page * pageSize)건이 추가로 skip되어 row 누락 발생.
          @Override
          public int getPage() {
            return 0;
          }

          @Override
          public Settlement read() throws Exception {
            queryProvider.setLastId(lastIdSeen);
            Settlement item = super.read();
            if (item != null) {
              lastIdSeen = item.getId();
            }
            return item;
          }
        };

    reader.setName("settlementReader");
    reader.setQueryProvider(queryProvider);
    reader.setParameterValues(parameters);
    reader.setPageSize(chunkSize);
    reader.setTransacted(false);
    return reader;
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
