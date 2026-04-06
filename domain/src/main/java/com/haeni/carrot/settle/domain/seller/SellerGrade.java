package com.haeni.carrot.settle.domain.seller;

import java.math.BigDecimal;

public enum SellerGrade {
  STANDARD(new BigDecimal("0.05")),
  PREMIUM(new BigDecimal("0.03")),
  VIP(new BigDecimal("0.01"));

  private final BigDecimal platformFeeRate;

  SellerGrade(BigDecimal platformFeeRate) {
    this.platformFeeRate = platformFeeRate;
  }

  public BigDecimal getPlatformFeeRate() {
    return platformFeeRate;
  }
}