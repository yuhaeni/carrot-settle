package com.haeni.carrot.settle.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderItem;
import com.haeni.carrot.settle.domain.order.OrderStatus;
import com.haeni.carrot.settle.domain.product.Product;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.infrastructure.order.OrderRepository;
import com.haeni.carrot.settle.infrastructure.product.ProductRepository;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import com.haeni.carrot.settle.order.dto.CreateOrderRequest;
import com.haeni.carrot.settle.order.dto.OrderItemRequest;
import com.haeni.carrot.settle.order.dto.OrderResponseDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ProductRepository productRepository;
  @Mock private SettlementRepository settlementRepository;
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
    OrderResponseDto response = orderService.createOrder(request);

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
    OrderResponseDto response = orderService.createOrder(request);

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

  // ===================== confirmOrder =====================

  @Test
  @DisplayName("PAID 상태 주문 확정 시 CONFIRMED 상태로 변경되고 Settlement가 생성된다")
  void confirmOrder_성공_단일셀러() {
    // given
    Seller seller = new Seller("셀러A", "seller@test.com", SellerGrade.STANDARD);
    Product product = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);

    Order order = new Order(BigDecimal.valueOf(10000));
    order.addOrderItem(new OrderItem(order, product, 1, BigDecimal.valueOf(10000)));

    given(orderRepository.findByIdWithItemsAndSeller(1L)).willReturn(Optional.of(order));
    given(settlementRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

    // when
    OrderResponseDto response = orderService.confirmOrder(1L);

    // then
    assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);

    ArgumentCaptor<List<Settlement>> captor = ArgumentCaptor.forClass(List.class);
    then(settlementRepository).should().saveAll(captor.capture());

    List<Settlement> settlements = captor.getValue();
    assertThat(settlements).hasSize(1);

    Settlement settlement = settlements.get(0);
    assertThat(settlement.getSeller()).isEqualTo(seller);
    assertThat(settlement.getTotalAmount()).isEqualByComparingTo("10000");
    assertThat(settlement.getPgFee()).isEqualByComparingTo("300.00");       // 10000 * 3%
    assertThat(settlement.getPlatformFee()).isEqualByComparingTo("500.00"); // 10000 * 5% (STANDARD)
    assertThat(settlement.getNetAmount()).isEqualByComparingTo("9200.00");  // 10000 - 300 - 500
  }

  @Test
  @DisplayName("복수 셀러 주문 확정 시 셀러별 Settlement가 각각 생성된다")
  void confirmOrder_성공_복수셀러() {
    // given
    Seller sellerA = new Seller("셀러A", "a@test.com", SellerGrade.STANDARD); // 플랫폼 수수료 5%
    Seller sellerB = new Seller("셀러B", "b@test.com", SellerGrade.VIP);      // 플랫폼 수수료 1%
    Product productA = new Product(sellerA, "상품A", BigDecimal.valueOf(10000), 100);
    Product productB = new Product(sellerB, "상품B", BigDecimal.valueOf(20000), 50);

    Order order = new Order(BigDecimal.valueOf(30000));
    order.addOrderItem(new OrderItem(order, productA, 1, BigDecimal.valueOf(10000)));
    order.addOrderItem(new OrderItem(order, productB, 1, BigDecimal.valueOf(20000)));

    given(orderRepository.findByIdWithItemsAndSeller(1L)).willReturn(Optional.of(order));
    given(settlementRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

    // when
    orderService.confirmOrder(1L);

    // then
    ArgumentCaptor<List<Settlement>> captor = ArgumentCaptor.forClass(List.class);
    then(settlementRepository).should().saveAll(captor.capture());

    List<Settlement> settlements = captor.getValue();
    assertThat(settlements).hasSize(2);

    Settlement settlementA = settlements.get(0);
    assertThat(settlementA.getSeller()).isEqualTo(sellerA);
    assertThat(settlementA.getPgFee()).isEqualByComparingTo("300.00");       // 10000 * 3%
    assertThat(settlementA.getPlatformFee()).isEqualByComparingTo("500.00"); // 10000 * 5%
    assertThat(settlementA.getNetAmount()).isEqualByComparingTo("9200.00");

    Settlement settlementB = settlements.get(1);
    assertThat(settlementB.getSeller()).isEqualTo(sellerB);
    assertThat(settlementB.getPgFee()).isEqualByComparingTo("600.00");       // 20000 * 3%
    assertThat(settlementB.getPlatformFee()).isEqualByComparingTo("200.00"); // 20000 * 1%
    assertThat(settlementB.getNetAmount()).isEqualByComparingTo("19200.00");
  }

  @Test
  @DisplayName("존재하지 않는 주문 ID로 확정 시 BusinessException이 발생한다")
  void confirmOrder_존재하지않는주문_예외() {
    // given
    given(orderRepository.findByIdWithItemsAndSeller(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> orderService.confirmOrder(999L))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("이미 확정된 주문을 다시 확정 시 BusinessException이 발생한다")
  void confirmOrder_이미확정된주문_예외() {
    // given
    Seller seller = new Seller("셀러A", "seller@test.com", SellerGrade.STANDARD);
    Product product = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);

    Order order = new Order(BigDecimal.valueOf(10000));
    order.addOrderItem(new OrderItem(order, product, 1, BigDecimal.valueOf(10000)));
    order.confirm(); // PAID → CONFIRMED

    given(orderRepository.findByIdWithItemsAndSeller(1L)).willReturn(Optional.of(order));

    // when & then — CONFIRMED → CONFIRMED 전이는 불가
    assertThatThrownBy(() -> orderService.confirmOrder(1L))
        .isInstanceOf(BusinessException.class);
  }

  // ===================== refundOrder =====================

  @Test
  @DisplayName("PAID 상태 주문 환불 시 REFUNDED 상태로 변경된다")
  void refundOrder_성공() {
    // given
    Seller seller = new Seller("셀러A", "seller@test.com", SellerGrade.STANDARD);
    Product product = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);

    Order order = new Order(BigDecimal.valueOf(10000));
    order.addOrderItem(new OrderItem(order, product, 1, BigDecimal.valueOf(10000)));

    given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

    // when
    OrderResponseDto response = orderService.refundOrder(1L);

    // then
    assertThat(response.status()).isEqualTo(OrderStatus.REFUNDED);
  }

  @Test
  @DisplayName("존재하지 않는 주문 환불 시 BusinessException이 발생한다")
  void refundOrder_존재하지않는주문_예외() {
    // given
    given(orderRepository.findByIdWithItems(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> orderService.refundOrder(999L))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("CONFIRMED 상태 주문 환불 시 BusinessException이 발생한다")
  void refundOrder_확정된주문_예외() {
    // given
    Seller seller = new Seller("셀러A", "seller@test.com", SellerGrade.STANDARD);
    Product product = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);

    Order order = new Order(BigDecimal.valueOf(10000));
    order.addOrderItem(new OrderItem(order, product, 1, BigDecimal.valueOf(10000)));
    order.confirm(); // PAID → CONFIRMED

    given(orderRepository.findByIdWithItems(1L)).willReturn(Optional.of(order));

    // when & then — CONFIRMED → REFUNDED 전이는 불가
    assertThatThrownBy(() -> orderService.refundOrder(1L))
        .isInstanceOf(BusinessException.class);
  }

  // ===================== findPaidOrderIdsOlderThan =====================

  @Test
  @DisplayName("7일 경과 PAID 주문 ID 목록을 반환한다")
  void findPaidOrderIdsOlderThan_성공() {
    // given
    LocalDateTime threshold = LocalDateTime.now().minusDays(7);
    given(orderRepository.findIdsByStatusAndCreatedAtBefore(OrderStatus.PAID, threshold))
        .willReturn(List.of(1L, 2L, 3L));

    // when
    List<Long> result = orderService.findPaidOrderIdsOlderThan(threshold);

    // then
    assertThat(result).containsExactly(1L, 2L, 3L);
  }
}
