package com.haeni.carrot.settle.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderStatus;
import com.haeni.carrot.settle.domain.product.Product;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import com.haeni.carrot.settle.infrastructure.order.OrderRepository;
import com.haeni.carrot.settle.infrastructure.product.ProductRepository;
import com.haeni.carrot.settle.order.dto.CreateOrderRequest;
import com.haeni.carrot.settle.order.dto.OrderItemRequest;
import com.haeni.carrot.settle.order.dto.OrderResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ProductRepository productRepository;
  @InjectMocks private OrderService orderService;

  @Test
  @DisplayName("유효한 상품으로 주문 생성 시 PAID 상태의 주문이 반환된다")
  void createOrder_성공() {
    // given
    Seller seller = new Seller("셀러A", "seller@test.com", SellerGrade.STANDARD);
    Product product = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);
    ReflectionTestUtils.setField(product, "id", 1L);

    CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1L, 2)));

    given(productRepository.findAllById(List.of(1L))).willReturn(List.of(product));
    given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

    // when
    OrderResponse response = orderService.createOrder(request);

    // then
    assertThat(response.status()).isEqualTo(OrderStatus.PAID);
    assertThat(response.totalAmount()).isEqualTo(20000L); // 10000 * 2
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).quantity()).isEqualTo(2);
    assertThat(response.items().get(0).unitPrice()).isEqualTo(10000L);
    assertThat(response.items().get(0).subtotal()).isEqualTo(20000L);
  }

  @Test
  @DisplayName("여러 상품이 포함된 주문의 totalAmount는 각 상품 금액의 합계다")
  void createOrder_여러상품_totalAmount_합계() {
    // given
    Seller seller = new Seller("셀러A", "seller@test.com", SellerGrade.STANDARD);
    Product p1 = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);
    Product p2 = new Product(seller, "상품B", BigDecimal.valueOf(5000), 50);
    ReflectionTestUtils.setField(p1, "id", 1L);
    ReflectionTestUtils.setField(p2, "id", 2L);

    CreateOrderRequest request =
        new CreateOrderRequest(
            List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 3)));

    given(productRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(p1, p2));
    given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

    // when
    OrderResponse response = orderService.createOrder(request);

    // then — p1: 10000*2=20000, p2: 5000*3=15000, total=35000
    assertThat(response.totalAmount()).isEqualTo(35000L);
    assertThat(response.items()).hasSize(2);
  }

  @Test
  @DisplayName("존재하지 않는 상품 ID로 주문 생성 시 BusinessException이 발생한다")
  void createOrder_존재하지않는상품_예외() {
    // given
    CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(999L, 1)));
    given(productRepository.findAllById(List.of(999L))).willReturn(List.of());

    // when & then
    assertThatThrownBy(() -> orderService.createOrder(request))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("일부 상품만 존재할 때 BusinessException이 발생한다")
  void createOrder_일부상품_없음_예외() {
    // given
    Seller seller = new Seller("셀러A", "seller@test.com", SellerGrade.STANDARD);
    Product product = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);
    ReflectionTestUtils.setField(product, "id", 1L);

    CreateOrderRequest request =
        new CreateOrderRequest(
            List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(999L, 1)));
    given(productRepository.findAllById(List.of(1L, 999L))).willReturn(List.of(product));

    // when & then — 반환된 상품이 1개, 요청은 2개 → 불일치
    assertThatThrownBy(() -> orderService.createOrder(request))
        .isInstanceOf(BusinessException.class);
  }
}
