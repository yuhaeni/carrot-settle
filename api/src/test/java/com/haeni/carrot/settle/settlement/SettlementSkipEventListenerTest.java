package com.haeni.carrot.settle.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementSkipLog;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementSkipLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementSkipEventListenerTest {

  @Mock private SettlementSkipLogRepository skipLogRepository;
  @Mock private SettlementRepository settlementRepository;

  @Test
  @DisplayName("skipCount가 임계치 미만이면 INCOMPLETED를 유지하고 skipCount만 증가한다")
  void threshold_미만_시_INCOMPLETED_유지() {
    SettlementSkipEventListener listener =
        new SettlementSkipEventListener(skipLogRepository, settlementRepository);
    ReflectionTestUtils.setField(listener, "skipBlockThreshold", 3);

    Settlement settlement = newSettlement();
    ReflectionTestUtils.setField(settlement, "id", 42L);
    when(settlementRepository.findById(42L)).thenReturn(Optional.of(settlement));

    listener.handle(newEvent(42L));

    assertThat(settlement.getSkipCount()).isEqualTo(1);
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.INCOMPLETED);
    verify(skipLogRepository).save(any(SettlementSkipLog.class));
  }

  @Test
  @DisplayName("skipCount가 임계치 도달 시 BLOCKED로 전이된다")
  void threshold_도달_시_BLOCKED_전이() {
    SettlementSkipEventListener listener =
        new SettlementSkipEventListener(skipLogRepository, settlementRepository);
    ReflectionTestUtils.setField(listener, "skipBlockThreshold", 1);

    Settlement settlement = newSettlement();
    ReflectionTestUtils.setField(settlement, "id", 99L);
    when(settlementRepository.findById(99L)).thenReturn(Optional.of(settlement));

    listener.handle(newEvent(99L));

    assertThat(settlement.getSkipCount()).isEqualTo(1);
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.BLOCKED);
    verify(skipLogRepository).save(any(SettlementSkipLog.class));
  }

  @Test
  @DisplayName("Settlement가 이미 삭제되어 없어도 skip 로그는 적재된다")
  void settlement_없음_skip_로그만_적재() {
    SettlementSkipEventListener listener =
        new SettlementSkipEventListener(skipLogRepository, settlementRepository);
    ReflectionTestUtils.setField(listener, "skipBlockThreshold", 3);

    when(settlementRepository.findById(123L)).thenReturn(Optional.empty());

    listener.handle(newEvent(123L));

    verify(skipLogRepository).save(any(SettlementSkipLog.class));
  }

  private Settlement newSettlement() {
    Seller seller = new Seller("테스트셀러", "test@example.com", SellerGrade.STANDARD);
    BigDecimal totalAmount = new BigDecimal("10000");
    FeeDetail fee = new FeeDetail(new BigDecimal("300"), new BigDecimal("500"));
    return new Settlement(seller, LocalDate.now(), totalAmount, fee, new BigDecimal("-100"));
  }

  private SettlementSkippedEvent newEvent(Long settlementId) {
    return new SettlementSkippedEvent(
        settlementId,
        SettlementStatus.INCOMPLETED,
        new BigDecimal("-100"),
        "[NEGATIVE_AMOUNT] settlementId=" + settlementId + ", netAmount=-100",
        LocalDateTime.now());
  }
}
