package com.haeni.carrot.settle.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.TestcontainersConfiguration;
import com.haeni.carrot.settle.domain.order.OrderStatus;
import com.haeni.carrot.settle.domain.product.Product;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import com.haeni.carrot.settle.infrastructure.product.ProductRepository;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import com.haeni.carrot.settle.order.dto.CreateOrderRequest;
import com.haeni.carrot.settle.order.dto.OrderItemRequest;
import com.haeni.carrot.settle.order.dto.OrderResponseDto;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성 → PAID → 구매 확정 → 수수료 계산 → Settlement 적재까지의 전체 흐름을 실제 DB(Testcontainers
 * PostgreSQL)와 통합하여 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderSettlementIntegrationTest {

  @Autowired private OrderService orderService;
  @Autowired private ProductRepository productRepository;
  @Autowired private SettlementRepository settlementRepository;

  @Test
  @DisplayName("단일 셀러 주문 생성 → 확정 시 Settlement에 FeeDetail이 정확히 적재된다")
  void 주문생성_확정_수수료계산_통합() {
    // given — V3 seed의 STANDARD 셀러(김철수) 상품 사용
    Product product = findFirstProductByGrade(SellerGrade.STANDARD);
    BigDecimal unitPrice = product.getPrice();
    int quantity = 2;
    BigDecimal expectedTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

    // when — 주문 생성 (즉시 PAID)
    OrderResponseDto created =
        orderService.createOrder(
            new CreateOrderRequest(List.of(new OrderItemRequest(product.getId(), quantity))));

    assertThat(created.status()).isEqualTo(OrderStatus.PAID);
    assertThat(created.totalAmount()).isEqualTo(expectedTotal.longValueExact());

    // when — 구매 확정
    OrderResponseDto confirmed = orderService.confirmOrder(created.id());
    assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);

    // then — Settlement 적재 검증
    List<Settlement> settlements = settlementRepository.findAll();
    assertThat(settlements).hasSize(1);

    Settlement settlement = settlements.get(0);
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.INCOMPLETED);
    assertThat(settlement.getSeller().getId()).isEqualTo(product.getSeller().getId());
    assertThat(settlement.getTotalAmount()).isEqualByComparingTo(expectedTotal);

    // STANDARD 셀러: PG 3% + 플랫폼 5% = 8%
    BigDecimal expectedPgFee = expectedTotal.multiply(new BigDecimal("0.03")).setScale(0, java.math.RoundingMode.HALF_UP);
    BigDecimal expectedPlatformFee = expectedTotal.multiply(new BigDecimal("0.05")).setScale(0, java.math.RoundingMode.HALF_UP);
    BigDecimal expectedTotalFee = expectedPgFee.add(expectedPlatformFee);
    BigDecimal expectedNetAmount = expectedTotal.subtract(expectedTotalFee);

    assertThat(settlement.getFeeDetail().getPgFee()).isEqualByComparingTo(expectedPgFee);
    assertThat(settlement.getFeeDetail().getPlatformFee()).isEqualByComparingTo(expectedPlatformFee);
    assertThat(settlement.getFeeDetail().getTotalFee()).isEqualByComparingTo(expectedTotalFee);
    assertThat(settlement.getNetAmount()).isEqualByComparingTo(expectedNetAmount);
  }

  @Test
  @DisplayName("복수 셀러(STANDARD + VIP) 주문 확정 시 셀러별 Settlement가 각각 생성된다")
  void 복수셀러_주문확정_셀러별_정산생성() {
    // given — STANDARD 셀러 상품 + VIP 셀러 상품
    Product standardProduct = findFirstProductByGrade(SellerGrade.STANDARD);
    Product vipProduct = findFirstProductByGrade(SellerGrade.VIP);

    // when — 두 셀러 상품을 섞어 주문
    OrderResponseDto created =
        orderService.createOrder(
            new CreateOrderRequest(
                List.of(
                    new OrderItemRequest(standardProduct.getId(), 1),
                    new OrderItemRequest(vipProduct.getId(), 1))));

    orderService.confirmOrder(created.id());

    // then — 셀러별로 Settlement 2건 생성
    List<Settlement> settlements =
        settlementRepository.findAll().stream()
            .sorted(Comparator.comparing(s -> s.getSeller().getGrade()))
            .toList();

    assertThat(settlements).hasSize(2);

    Settlement standardSettlement =
        settlements.stream()
            .filter(s -> s.getSeller().getGrade() == SellerGrade.STANDARD)
            .findFirst()
            .orElseThrow();
    Settlement vipSettlement =
        settlements.stream()
            .filter(s -> s.getSeller().getGrade() == SellerGrade.VIP)
            .findFirst()
            .orElseThrow();

    // STANDARD: 플랫폼 5%, VIP: 플랫폼 1%
    assertThat(standardSettlement.getFeeDetail().getPlatformFee())
        .isEqualByComparingTo(
            standardProduct.getPrice().multiply(new BigDecimal("0.05")).setScale(0, java.math.RoundingMode.HALF_UP));
    assertThat(vipSettlement.getFeeDetail().getPlatformFee())
        .isEqualByComparingTo(
            vipProduct.getPrice().multiply(new BigDecimal("0.01")).setScale(0, java.math.RoundingMode.HALF_UP));

    // PG 수수료는 두 셀러 모두 3% 동일
    assertThat(standardSettlement.getFeeDetail().getPgFee())
        .isEqualByComparingTo(
            standardProduct.getPrice().multiply(new BigDecimal("0.03")).setScale(0, java.math.RoundingMode.HALF_UP));
    assertThat(vipSettlement.getFeeDetail().getPgFee())
        .isEqualByComparingTo(
            vipProduct.getPrice().multiply(new BigDecimal("0.03")).setScale(0, java.math.RoundingMode.HALF_UP));
  }

  @Test
  @DisplayName("netAmount = totalAmount - totalFee 불변식이 실제 DB 적재 후에도 성립한다")
  void netAmount_불변식_검증() {
    Product product = findFirstProductByGrade(SellerGrade.PREMIUM);

    OrderResponseDto created =
        orderService.createOrder(
            new CreateOrderRequest(List.of(new OrderItemRequest(product.getId(), 3))));
    orderService.confirmOrder(created.id());

    Settlement settlement = settlementRepository.findAll().get(0);

    assertThat(settlement.getNetAmount())
        .isEqualByComparingTo(
            settlement.getTotalAmount().subtract(settlement.getFeeDetail().getTotalFee()));
  }

  private Product findFirstProductByGrade(SellerGrade grade) {
    return productRepository.findAll().stream()
        .filter(p -> p.getSeller().getGrade() == grade)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("V3 seed에 " + grade + " 셀러 상품이 없습니다"));
  }
}
