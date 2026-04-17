package com.haeni.carrot.settle.domain.seller;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SellerGrade {
  STANDARD(new BigDecimal("0.05")),
  PREMIUM(new BigDecimal("0.03")),
  VIP(new BigDecimal("0.01"));

  private final BigDecimal platformFeeRate;
}