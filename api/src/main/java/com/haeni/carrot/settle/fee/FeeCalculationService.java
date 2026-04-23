package com.haeni.carrot.settle.fee;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.fee.PgFeeCalculator;
import com.haeni.carrot.settle.domain.fee.PlatformFeeCalculator;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class FeeCalculationService {

  private final PgFeeCalculator pgFeeCalculator;
  private final PlatformFeeCalculator platformFeeCalculator;

  public FeeCalculationService(
      PgFeeCalculator pgFeeCalculator, PlatformFeeCalculator platformFeeCalculator) {
    this.pgFeeCalculator = pgFeeCalculator;
    this.platformFeeCalculator = platformFeeCalculator;
  }

  public FeeCalculationBuilder builder(BigDecimal totalAmount) {
    return new FeeCalculationBuilder(totalAmount, pgFeeCalculator, platformFeeCalculator);
  }

  public FeeDetail calculate(BigDecimal totalAmount, SellerGrade grade) {
    return builder(totalAmount).withPgFee().withPlatformFee(grade).build();
  }
}
