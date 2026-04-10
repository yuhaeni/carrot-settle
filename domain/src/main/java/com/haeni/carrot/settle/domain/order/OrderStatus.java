package com.haeni.carrot.settle.domain.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public enum OrderStatus {
  CREATED("주문 생성", "주문이 생성되었으나 아직 결제가 완료되지 않은 상태"),
  PAID("결제 완료", "결제가 완료된 상태. 주문 생성과 동시에 진입하며 구매 확정 대기 중"),
  CONFIRMED("구매 확정", "구매자가 수령을 확정한 상태. 정산 대상으로 전환됨"),
  SETTLED("정산 완료", "판매자에게 대금 정산이 완료된 최종 상태"),
  REFUNDED("환불 완료", "결제 완료 후 환불이 처리된 상태. 정산 대상에서 제외됨");

  private final String name;
  private final String description;

  OrderStatus(String name, String description) {
    this.name = name;
    this.description = description;
  }

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
