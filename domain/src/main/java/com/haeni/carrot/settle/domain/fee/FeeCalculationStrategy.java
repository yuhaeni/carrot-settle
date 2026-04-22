package com.haeni.carrot.settle.domain.fee;

import java.math.BigDecimal;

public interface FeeCalculationStrategy {

  BigDecimal calculate(BigDecimal amount);
}
