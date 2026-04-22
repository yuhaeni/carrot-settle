package com.haeni.carrot.settle.fee;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.fee.PgFeeStrategy;
import com.haeni.carrot.settle.domain.fee.PlatformFeeStrategy;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class FeeCalculationService {

  private final PgFeeStrategy pgFeeStrategy = new PgFeeStrategy();

  public FeeDetail calculate(BigDecimal totalAmount, SellerGrade grade) {
    BigDecimal pgFee = pgFeeStrategy.calculate(totalAmount);
    BigDecimal platformFee =
        new PlatformFeeStrategy(grade.getPlatformFeeRate()).calculate(totalAmount);
    return new FeeDetail(pgFee, platformFee);
  }
}
