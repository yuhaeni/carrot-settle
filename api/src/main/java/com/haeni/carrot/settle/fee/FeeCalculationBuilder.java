package com.haeni.carrot.settle.fee;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.fee.PgFeeCalculator;
import com.haeni.carrot.settle.domain.fee.PlatformFeeCalculator;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;

public class FeeCalculationBuilder {

  private final BigDecimal totalAmount;
  private final PgFeeCalculator pgFeeCalculator;
  private final PlatformFeeCalculator platformFeeCalculator;

  private BigDecimal pgFee = BigDecimal.ZERO;
  private BigDecimal platformFee = BigDecimal.ZERO;

  public FeeCalculationBuilder(
      BigDecimal totalAmount,
      PgFeeCalculator pgFeeCalculator,
      PlatformFeeCalculator platformFeeCalculator) {
    this.totalAmount = totalAmount;
    this.pgFeeCalculator = pgFeeCalculator;
    this.platformFeeCalculator = platformFeeCalculator;
  }

  public FeeCalculationBuilder withPgFee() {
    this.pgFee = pgFeeCalculator.calculate(totalAmount);
    return this;
  }

  public FeeCalculationBuilder withPlatformFee(SellerGrade grade) {
    this.platformFee = platformFeeCalculator.calculate(totalAmount, grade);
    return this;
  }

  public FeeDetail build() {
    return new FeeDetail(pgFee, platformFee);
  }
}
