package com.haeni.carrot.settle.settlement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haeni.carrot.settle.TestcontainersConfiguration;
import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `POST /api/v1/settlements/calculate`의 HTTP 응답 사이클 검증. SettlementBatchService에서 던진
 * BusinessException이 GlobalExceptionHandler를 거쳐 ErrorResponse(JSON)로 변환되는 흐름까지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SettlementCalculateApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SettlementRepository settlementRepository;

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
  @DisplayName("동일 targetDate 재호출 시 409 Conflict + BATCH_ALREADY_COMPLETED 응답")
  void 동일_targetDate_재호출_시_409_Conflict() throws Exception {
    LocalDate target = LocalDate.of(2026, 5, 1);
    settlementRepository.save(newSettlement(standardSeller, target.minusDays(1)));

    String body = """
        { "targetDate": "%s" }
        """.formatted(target);

    mockMvc
        .perform(post("/api/v1/settlements/calculate").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/v1/settlements/calculate").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BATCH_ALREADY_COMPLETED"))
        .andExpect(jsonPath("$.message").value("동일 기준일로 이미 완료된 정산 배치입니다."));
  }

  private Settlement newSettlement(Seller seller, LocalDate date) {
    BigDecimal totalAmount = new BigDecimal("10000");
    FeeDetail fee = new FeeDetail(new BigDecimal("300"), new BigDecimal("500"));
    BigDecimal netAmount = totalAmount.subtract(fee.getTotalFee());
    return new Settlement(seller, date, totalAmount, fee, netAmount);
  }
}
