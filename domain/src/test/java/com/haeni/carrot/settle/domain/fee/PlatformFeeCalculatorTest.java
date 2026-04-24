package com.haeni.carrot.settle.domain.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PlatformFeeCalculatorTest {

  private final PlatformFeeCalculator calculator = new PlatformFeeCalculator();

  @ParameterizedTest(name = "{0} 등급 10000원 → 플랫폼 수수료 {1}원")
  @CsvSource({
    "STANDARD, 500", // 10000 * 5%
    "PREMIUM, 300", // 10000 * 3%
    "VIP, 100" // 10000 * 1%
  })
  @DisplayName("등급별 요율이 올바르게 적용된다")
  void calculate_등급별(SellerGrade grade, String expected) {
    BigDecimal fee = calculator.calculate(new BigDecimal("10000"), grade);
    assertThat(fee).isEqualByComparingTo(expected);
  }

  @ParameterizedTest(name = "{0}원 STANDARD → {1}원")
  @CsvSource({
    // STANDARD 5% 경계값
    "0, 0",
    "1, 0", // 0.05 → HALF_UP → 0
    "10, 1", // 0.5 → HALF_UP → 1
    "9, 0", // 0.45 → HALF_UP → 0
    "999, 50", // 49.95 → 50
    "10000, 500"
  })
  @DisplayName("STANDARD 등급 경계값: 금액 × 5% HALF_UP")
  void calculate_STANDARD_경계값(String amount, String expected) {
    BigDecimal fee = calculator.calculate(new BigDecimal(amount), SellerGrade.STANDARD);
    assertThat(fee).isEqualByComparingTo(expected);
  }

  @ParameterizedTest(name = "{0}원 PREMIUM → {1}원")
  @CsvSource({
    "0, 0",
    "1, 0", // 0.03 → 0
    "17, 1", // 0.51 → 1
    "16, 0", // 0.48 → 0
    "999, 30", // 29.97 → 30
    "10000, 300"
  })
  @DisplayName("PREMIUM 등급 경계값: 금액 × 3% HALF_UP")
  void calculate_PREMIUM_경계값(String amount, String expected) {
    BigDecimal fee = calculator.calculate(new BigDecimal(amount), SellerGrade.PREMIUM);
    assertThat(fee).isEqualByComparingTo(expected);
  }

  @ParameterizedTest(name = "{0}원 VIP → {1}원")
  @CsvSource({
    "0, 0",
    "1, 0", // 0.01 → 0
    "50, 1", // 0.5 → 1
    "49, 0", // 0.49 → 0
    "999, 10", // 9.99 → 10
    "10000, 100"
  })
  @DisplayName("VIP 등급 경계값: 금액 × 1% HALF_UP")
  void calculate_VIP_경계값(String amount, String expected) {
    BigDecimal fee = calculator.calculate(new BigDecimal(amount), SellerGrade.VIP);
    assertThat(fee).isEqualByComparingTo(expected);
  }

  @ParameterizedTest
  @EnumSource(SellerGrade.class)
  @DisplayName("0원은 어떤 등급이든 플랫폼 수수료 0원")
  void calculate_0원은_모든등급에서_0원(SellerGrade grade) {
    BigDecimal fee = calculator.calculate(BigDecimal.ZERO, grade);
    assertThat(fee).isEqualByComparingTo("0");
  }
}
