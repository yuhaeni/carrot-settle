package com.haeni.carrot.settle.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyUtilTest {

  @Test
  @DisplayName("999원의 5%는 HALF_UP 반올림으로 50원이다")
  void multiply_소수점발생_반올림() {
    // 999 * 0.05 = 49.95 → HALF_UP → 50
    BigDecimal result = MoneyUtil.multiply(new BigDecimal("999"), new BigDecimal("0.05"));
    assertThat(result).isEqualByComparingTo("50");
  }

  @Test
  @DisplayName("0원에 어떤 비율을 곱해도 0원이다")
  void multiply_0원_경계값() {
    BigDecimal result = MoneyUtil.multiply(BigDecimal.ZERO, new BigDecimal("0.05"));
    assertThat(result).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("1원의 3%는 0.03 → HALF_UP → 0원이다")
  void multiply_1원_경계값() {
    // 1 * 0.03 = 0.03 → HALF_UP scale 0 → 0
    BigDecimal result = MoneyUtil.multiply(new BigDecimal("1"), new BigDecimal("0.03"));
    assertThat(result).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("10000원의 3%는 300원이다")
  void multiply_정수결과() {
    BigDecimal result = MoneyUtil.multiply(new BigDecimal("10000"), new BigDecimal("0.03"));
    assertThat(result).isEqualByComparingTo("300");
  }

  @Test
  @DisplayName("add는 두 금액을 더한다")
  void add_두금액합산() {
    BigDecimal result = MoneyUtil.add(new BigDecimal("300"), new BigDecimal("500"));
    assertThat(result).isEqualByComparingTo("800");
  }

  @Test
  @DisplayName("subtract는 두 금액을 뺀다")
  void subtract_금액차감() {
    BigDecimal result = MoneyUtil.subtract(new BigDecimal("10000"), new BigDecimal("800"));
    assertThat(result).isEqualByComparingTo("9200");
  }
}