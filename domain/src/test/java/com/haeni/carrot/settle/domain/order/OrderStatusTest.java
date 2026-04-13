package com.haeni.carrot.settle.domain.order;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

  @Test
  void CREATED에서_PAID로_전이_가능() {
    assertThatCode(() -> OrderStatus.CREATED.validateTransitionTo(OrderStatus.PAID))
        .doesNotThrowAnyException();
  }

  @Test
  void CREATED에서_CONFIRMED로_전이_불가() {
    assertThatThrownBy(() -> OrderStatus.CREATED.validateTransitionTo(OrderStatus.CONFIRMED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void PAID에서_CONFIRMED로_전이_가능() {
    assertThatCode(() -> OrderStatus.PAID.validateTransitionTo(OrderStatus.CONFIRMED))
        .doesNotThrowAnyException();
  }

  @Test
  void PAID에서_REFUNDED로_전이_가능() {
    assertThatCode(() -> OrderStatus.PAID.validateTransitionTo(OrderStatus.REFUNDED))
        .doesNotThrowAnyException();
  }

  @Test
  void CONFIRMED에서_SETTLED로_전이_가능() {
    assertThatCode(() -> OrderStatus.CONFIRMED.validateTransitionTo(OrderStatus.SETTLED))
        .doesNotThrowAnyException();
  }

  @Test
  void SETTLED에서_어떤_상태로도_전이_불가() {
    for (OrderStatus next : OrderStatus.values()) {
      assertThatThrownBy(() -> OrderStatus.SETTLED.validateTransitionTo(next))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void REFUNDED에서_어떤_상태로도_전이_불가() {
    for (OrderStatus next : OrderStatus.values()) {
      assertThatThrownBy(() -> OrderStatus.REFUNDED.validateTransitionTo(next))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
