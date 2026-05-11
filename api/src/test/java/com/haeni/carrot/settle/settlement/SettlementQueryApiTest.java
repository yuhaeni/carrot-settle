package com.haeni.carrot.settle.settlement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `GET /api/v1/settlements` QueryDSL 동적 쿼리 + 페이징 검증. sellerId/기간/상태 조건 조합과 페이징 응답 구조,
 * 그리고 PG·플랫폼·총 수수료가 분리되어 노출되는지 확인.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SettlementQueryApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SettlementRepository settlementRepository;

  @PersistenceContext private EntityManager em;

  private Seller standardSeller;
  private Seller premiumSeller;

  @BeforeEach
  void setUp() {
    settlementRepository.deleteAllInBatch();
    standardSeller = sellerByEmail("chulsoo@example.com");
    premiumSeller = sellerByEmail("younghee@example.com");
  }

  @Test
  @DisplayName("조건 없이 호출하면 전체 정산 내역이 페이징되어 반환된다")
  void search_조건없이_전체조회() throws Exception {
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 1)));
    settlementRepository.save(newSettlement(premiumSeller, LocalDate.of(2026, 5, 2)));
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 3)));

    mockMvc
        .perform(get("/api/v1/settlements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  @DisplayName("sellerId 필터로 호출하면 해당 셀러의 정산 내역만 반환된다")
  void search_sellerId_필터() throws Exception {
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 1)));
    settlementRepository.save(newSettlement(premiumSeller, LocalDate.of(2026, 5, 2)));
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 3)));

    mockMvc
        .perform(get("/api/v1/settlements").param("sellerId", standardSeller.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[*].sellerId", Matchers.everyItem(Matchers.is(standardSeller.getId().intValue()))));
  }

  @Test
  @DisplayName("startDate/endDate 필터로 호출하면 기간 내 정산 내역만 반환된다")
  void search_기간_필터() throws Exception {
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 4, 30)));
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 1)));
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 5)));
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 10)));

    mockMvc
        .perform(
            get("/api/v1/settlements")
                .param("startDate", "2026-05-01")
                .param("endDate", "2026-05-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("status 필터로 호출하면 해당 상태의 정산 내역만 반환된다")
  void search_status_필터() throws Exception {
    Settlement incompleted = newSettlement(standardSeller, LocalDate.of(2026, 5, 1));
    Settlement completed = newSettlement(standardSeller, LocalDate.of(2026, 5, 2));
    completed.complete();
    settlementRepository.save(incompleted);
    settlementRepository.save(completed);

    mockMvc
        .perform(get("/api/v1/settlements").param("status", "COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
  }

  @Test
  @DisplayName("응답에 pgFee, platformFee, totalFee, netAmount가 분리되어 노출된다")
  void search_수수료_분리표시() throws Exception {
    settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 1)));

    mockMvc
        .perform(get("/api/v1/settlements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].totalAmount").value(10000))
        .andExpect(jsonPath("$.content[0].pgFee").value(300))
        .andExpect(jsonPath("$.content[0].platformFee").value(500))
        .andExpect(jsonPath("$.content[0].totalFee").value(800))
        .andExpect(jsonPath("$.content[0].netAmount").value(9200));
  }

  @Test
  @DisplayName("page/size 파라미터로 페이징 응답이 분할된다")
  void search_페이징_분할() throws Exception {
    for (int i = 0; i < 5; i++) {
      settlementRepository.save(newSettlement(standardSeller, LocalDate.of(2026, 5, 1 + i)));
    }

    mockMvc
        .perform(get("/api/v1/settlements").param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  private Settlement newSettlement(Seller seller, LocalDate date) {
    BigDecimal totalAmount = new BigDecimal("10000");
    FeeDetail fee = new FeeDetail(new BigDecimal("300"), new BigDecimal("500"));
    BigDecimal netAmount = totalAmount.subtract(fee.getTotalFee());
    return new Settlement(seller, date, totalAmount, fee, netAmount);
  }

  private Seller sellerByEmail(String email) {
    return em.createQuery("SELECT s FROM Seller s WHERE s.email = :email", Seller.class)
        .setParameter("email", email)
        .getSingleResult();
  }
}
