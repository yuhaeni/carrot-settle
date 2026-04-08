package com.haeni.carrot.settle.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

  @Test
  void CREATED에서_PAID로_전이_가능() {
    assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PAID)).isTrue();
  }

  @Test
  void CREATED에서_CONFIRMED로_전이_불가() {
    assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
  }

  @Test
  void PAID에서_CONFIRMED로_전이_가능() {
    assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
  }

  @Test
  void PAID에서_REFUNDED로_전이_가능() {
    assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
  }

  @Test
  void CONFIRMED에서_SETTLED로_전이_가능() {
    assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SETTLED)).isTrue();
  }

  @Test
  void SETTLED에서_어떤_상태로도_전이_불가() {
    for (OrderStatus next : OrderStatus.values()) {
      assertThat(OrderStatus.SETTLED.canTransitionTo(next)).isFalse();
    }
  }

  @Test
  void REFUNDED에서_어떤_상태로도_전이_불가() {
    for (OrderStatus next : OrderStatus.values()) {
      assertThat(OrderStatus.REFUNDED.canTransitionTo(next)).isFalse();
    }
  }
}
