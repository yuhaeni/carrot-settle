package com.haeni.carrot.settle.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementItemProcessorTest {

  private final SettlementItemProcessor processor = new SettlementItemProcessor();

  @Test
  @DisplayName("INCOMPLETED Settlement는 COMPLETED로 전이된다")
  void process_INCOMPLETED는_COMPLETED로_전이() {
    Settlement settlement = newSettlement(new BigDecimal("9200"));
    ReflectionTestUtils.setField(settlement, "id", 1L);

    Settlement result = processor.process(settlement);

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
  }

  @Test
  @DisplayName("이미 COMPLETED인 Settlement는 null을 반환하여 skip 처리된다")
  void process_이미_COMPLETED는_null_반환() {
    Settlement settlement = newSettlement(new BigDecimal("9200"));
    ReflectionTestUtils.setField(settlement, "id", 2L);
    settlement.complete(); // INCOMPLETED → COMPLETED

    Settlement result = processor.process(settlement);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("음수 netAmount는 IllegalStateException이 발생하여 fault-tolerant로 skip 처리된다")
  void process_음수_netAmount는_예외() {
    Settlement settlement = newSettlement(new BigDecimal("-100"));
    ReflectionTestUtils.setField(settlement, "id", 3L);

    assertThatThrownBy(() -> processor.process(settlement))
        .isInstanceOf(SettlementSkippableException.class)
        .hasMessageContaining("NEGATIVE_AMOUNT")
        .hasMessageContaining("settlementId=3")
        .hasMessageContaining("netAmount=-100");
  }

  private Settlement newSettlement(BigDecimal netAmount) {
    Seller seller = new Seller("테스트셀러", "test@example.com", SellerGrade.STANDARD);
    BigDecimal totalAmount = new BigDecimal("10000");
    FeeDetail fee = new FeeDetail(new BigDecimal("300"), new BigDecimal("500"));
    return new Settlement(seller, LocalDate.now(), totalAmount, fee, netAmount);
  }
}
