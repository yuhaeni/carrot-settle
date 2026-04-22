package com.haeni.carrot.settle.domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

  private static final int SCALE = 0;
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private MoneyUtil() {}

  public static BigDecimal multiply(BigDecimal amount, BigDecimal rate) {
    return amount.multiply(rate).setScale(SCALE, ROUNDING_MODE);
  }

  public static BigDecimal add(BigDecimal a, BigDecimal b) {
    return a.add(b);
  }

  public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
    return a.subtract(b);
  }
}
