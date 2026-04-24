package com.haeni.carrot.settle.domain.fee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PgFeeCalculatorTest {

  private final PgFeeCalculator calculator = new PgFeeCalculator();

  @Test
  @DisplayName("10000원의 PG 수수료는 3% → 300원")
  void calculate_기본() {
    BigDecimal fee = calculator.calculate(new BigDecimal("10000"));
    assertThat(fee).isEqualByComparingTo("300");
  }

  @ParameterizedTest(name = "{0}원 → PG 수수료 {1}원")
  @CsvSource({
    // 경계값: 0원, 1원은 반올림으로 0원
    "0, 0",
    "1, 0",
    // 17원 * 0.03 = 0.51 → HALF_UP → 1원 (반올림 경계)
    "17, 1",
    // 16원 * 0.03 = 0.48 → HALF_UP → 0원
    "16, 0",
    // 999원 * 0.03 = 29.97 → 30원
    "999, 30",
    // 1000원 * 0.03 = 30원
    "1000, 30",
    // 100000원 * 0.03 = 3000원
    "100000, 3000",
    // 1234567원 * 0.03 = 37037.01 → 37037원
    "1234567, 37037"
  })
  @DisplayName("PG 수수료는 금액 × 3% HALF_UP 반올림 결과와 일치한다")
  void calculate_경계값(String amount, String expected) {
    BigDecimal fee = calculator.calculate(new BigDecimal(amount));
    assertThat(fee).isEqualByComparingTo(expected);
  }

  @Test
  @DisplayName("동일 금액에 대한 반복 호출 결과는 항상 같다 (멱등성)")
  void calculate_멱등성() {
    BigDecimal amount = new BigDecimal("9999");
    BigDecimal first = calculator.calculate(amount);
    BigDecimal second = calculator.calculate(amount);
    BigDecimal third = calculator.calculate(amount);
    assertThat(first).isEqualByComparingTo(second).isEqualByComparingTo(third);
  }
}