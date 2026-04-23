package com.haeni.carrot.settle.domain.fee;

import com.haeni.carrot.settle.domain.common.MoneyUtil;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;

public class PlatformFeeCalculator {

  public BigDecimal calculate(BigDecimal amount, SellerGrade grade) {
    return MoneyUtil.multiply(amount, grade.getPlatformFeeRate());
  }
}