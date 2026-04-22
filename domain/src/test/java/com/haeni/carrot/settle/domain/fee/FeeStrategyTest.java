package com.haeni.carrot.settle.domain.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeeStrategyTest {

  private final FeeCalculationStrategy pgFeeStrategy = new PgFeeStrategy();

  // ===================== PgFeeStrategy =====================

  @Test
  @DisplayName("10000원의 PG 수수료는 300원이다 (3%)")
  void pgFee_일반금액() {
    assertThat(pgFeeStrategy.calculate(new BigDecimal("10000"))).isEqualByComparingTo("300");
  }

  @Test
  @DisplayName("999원의 PG 수수료는 30원이다 (29.97 → HALF_UP → 30)")
  void pgFee_소수점반올림() {
    // 999 * 0.03 = 29.97 → HALF_UP → 30
    assertThat(pgFeeStrategy.calculate(new BigDecimal("999"))).isEqualByComparingTo("30");
  }

  @Test
  @DisplayName("0원의 PG 수수료는 0원이다")
  void pgFee_0원_경계값() {
    assertThat(pgFeeStrategy.calculate(BigDecimal.ZERO)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("1원의 PG 수수료는 0원이다 (0.03 → HALF_UP → 0)")
  void pgFee_1원_경계값() {
    assertThat(pgFeeStrategy.calculate(new BigDecimal("1"))).isEqualByComparingTo("0");
  }

  // ===================== PlatformFeeStrategy =====================

  @Test
  @DisplayName("10000원 STANDARD 등급 플랫폼 수수료는 500원이다 (5%)")
  void platformFee_STANDARD() {
    FeeCalculationStrategy strategy =
        new PlatformFeeStrategy(SellerGrade.STANDARD.getPlatformFeeRate());
    assertThat(strategy.calculate(new BigDecimal("10000"))).isEqualByComparingTo("500");
  }

  @Test
  @DisplayName("10000원 PREMIUM 등급 플랫폼 수수료는 300원이다 (3%)")
  void platformFee_PREMIUM() {
    FeeCalculationStrategy strategy =
        new PlatformFeeStrategy(SellerGrade.PREMIUM.getPlatformFeeRate());
    assertThat(strategy.calculate(new BigDecimal("10000"))).isEqualByComparingTo("300");
  }

  @Test
  @DisplayName("10000원 VIP 등급 플랫폼 수수료는 100원이다 (1%)")
  void platformFee_VIP() {
    FeeCalculationStrategy strategy =
        new PlatformFeeStrategy(SellerGrade.VIP.getPlatformFeeRate());
    assertThat(strategy.calculate(new BigDecimal("10000"))).isEqualByComparingTo("100");
  }

  @Test
  @DisplayName("999원 STANDARD 등급 플랫폼 수수료는 50원이다 (49.95 → HALF_UP → 50)")
  void platformFee_소수점반올림_STANDARD() {
    FeeCalculationStrategy strategy =
        new PlatformFeeStrategy(SellerGrade.STANDARD.getPlatformFeeRate());
    assertThat(strategy.calculate(new BigDecimal("999"))).isEqualByComparingTo("50");
  }

  @Test
  @DisplayName("셀러 등급이 변경되면 새 요율이 적용된다 (OCP 검증)")
  void platformFee_OCP_새구현체_추가() {
    // 새 등급 추가 시 PlatformFeeStrategy 구현체 1개만 생성하면 됨
    FeeCalculationStrategy newGradeStrategy = new PlatformFeeStrategy(new BigDecimal("0.02"));
    assertThat(newGradeStrategy.calculate(new BigDecimal("10000"))).isEqualByComparingTo("200");
  }
}
