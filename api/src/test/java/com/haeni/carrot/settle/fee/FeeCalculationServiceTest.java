package com.haeni.carrot.settle.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeeCalculationServiceTest {

  private final FeeCalculationService service = new FeeCalculationService();

  @Test
  @DisplayName("STANDARD 등급 10000원: PG 300, 플랫폼 500, 총 800")
  void calculate_STANDARD() {
    FeeDetail fee = service.calculate(new BigDecimal("10000"), SellerGrade.STANDARD);

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("500");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("800");
  }

  @Test
  @DisplayName("PREMIUM 등급 10000원: PG 300, 플랫폼 300, 총 600")
  void calculate_PREMIUM() {
    FeeDetail fee = service.calculate(new BigDecimal("10000"), SellerGrade.PREMIUM);

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("300");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("600");
  }

  @Test
  @DisplayName("VIP 등급 10000원: PG 300, 플랫폼 100, 총 400")
  void calculate_VIP() {
    FeeDetail fee = service.calculate(new BigDecimal("10000"), SellerGrade.VIP);

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("100");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("400");
  }

  @Test
  @DisplayName("totalFee = pgFee + platformFee 합산이 정확하다")
  void calculate_totalFee는_pgFee_plus_platformFee() {
    FeeDetail fee = service.calculate(new BigDecimal("999"), SellerGrade.STANDARD);

    // 999 * 0.03 = 29.97 → 30, 999 * 0.05 = 49.95 → 50, total = 80
    assertThat(fee.getTotalFee())
        .isEqualByComparingTo(fee.getPgFee().add(fee.getPlatformFee()));
  }
}
