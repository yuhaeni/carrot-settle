package com.haeni.carrot.settle.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementSkipListenerTest {

  @Mock private ApplicationEventPublisher eventPublisher;

  @Test
  @DisplayName("onSkipInProcess는 SettlementSkippedEvent를 발행한다")
  void onSkipInProcess_이벤트_발행() {
    SettlementSkipListener listener = new SettlementSkipListener(eventPublisher);
    Settlement settlement = newSettlement(new BigDecimal("-100"));
    ReflectionTestUtils.setField(settlement, "id", 42L);
    IllegalStateException cause = new IllegalStateException("음수 정산금: settlementId=42, netAmount=-100");

    listener.onSkipInProcess(settlement, cause);

    ArgumentCaptor<SettlementSkippedEvent> captor =
        ArgumentCaptor.forClass(SettlementSkippedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    SettlementSkippedEvent event = captor.getValue();
    assertThat(event.settlementId()).isEqualTo(42L);
    assertThat(event.status()).isEqualTo(SettlementStatus.INCOMPLETED);
    assertThat(event.netAmount()).isEqualByComparingTo("-100");
    assertThat(event.reason()).isEqualTo(cause.getMessage());
    assertThat(event.occurredAt()).isNotNull();
  }

  private Settlement newSettlement(BigDecimal netAmount) {
    Seller seller = new Seller("테스트셀러", "test@example.com", SellerGrade.STANDARD);
    BigDecimal totalAmount = new BigDecimal("10000");
    FeeDetail fee = new FeeDetail(new BigDecimal("300"), new BigDecimal("500"));
    return new Settlement(seller, LocalDate.now(), totalAmount, fee, netAmount);
  }
}