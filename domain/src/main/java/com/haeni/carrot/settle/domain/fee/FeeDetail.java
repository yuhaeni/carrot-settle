package com.haeni.carrot.settle.domain.fee;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Embeddable
public class FeeDetail {

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal pgFee;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal platformFee;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal totalFee;

  public FeeDetail(BigDecimal pgFee, BigDecimal platformFee) {
    this.pgFee = pgFee;
    this.platformFee = platformFee;
    this.totalFee = pgFee.add(platformFee);
  }
}
