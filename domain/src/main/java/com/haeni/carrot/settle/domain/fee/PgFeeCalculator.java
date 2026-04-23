package com.haeni.carrot.settle.domain.fee;

import com.haeni.carrot.settle.domain.common.MoneyUtil;
import java.math.BigDecimal;

public class PgFeeCalculator {

  private static final BigDecimal RATE = new BigDecimal("0.03");

  public BigDecimal calculate(BigDecimal amount) {
    return MoneyUtil.multiply(amount, RATE);
  }
}
