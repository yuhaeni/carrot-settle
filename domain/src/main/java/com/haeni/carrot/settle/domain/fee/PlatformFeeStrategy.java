package com.haeni.carrot.settle.domain.fee;

import com.haeni.carrot.settle.domain.common.MoneyUtil;
import java.math.BigDecimal;

public class PlatformFeeStrategy implements FeeCalculationStrategy {

  private final BigDecimal rate;

  public PlatformFeeStrategy(BigDecimal rate) {
    this.rate = rate;
  }

  @Override
  public BigDecimal calculate(BigDecimal amount) {
    return MoneyUtil.multiply(amount, rate);
  }
}