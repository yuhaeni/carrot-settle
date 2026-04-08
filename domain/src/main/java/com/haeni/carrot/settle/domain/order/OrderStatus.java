package com.haeni.carrot.settle.domain.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
  CREATED,
  PAID,
  CONFIRMED,
  SETTLED,
  REFUNDED;

  private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
      Map.of(
          CREATED, EnumSet.of(PAID),
          PAID, EnumSet.of(CONFIRMED, REFUNDED),
          CONFIRMED, EnumSet.of(SETTLED),
          SETTLED, EnumSet.noneOf(OrderStatus.class),
          REFUNDED, EnumSet.noneOf(OrderStatus.class));

  public boolean canTransitionTo(OrderStatus next) {
    return TRANSITIONS.get(this).contains(next);
  }
}
