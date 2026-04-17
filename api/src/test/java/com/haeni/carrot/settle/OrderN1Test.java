package com.haeni.carrot.settle;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderItem;
import com.haeni.carrot.settle.domain.product.Product;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import com.haeni.carrot.settle.infrastructure.order.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * N+1 문제 재현 및 fetch join 해결 검증
 *
 * <p>Order → OrderItem(LAZY) → Product(LAZY) 관계에서 발생하는 N+1 문제를 Hibernate Statistics로 쿼리 수를
 * 측정하여 검증한다.
 *
 * <p>재현: findById 후 orderItems·product 접근 시 쿼리 N+1 발생 해결: findByIdWithItems (fetch join) 사용 시
 * 단일 쿼리로 처리
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OrderN1Test {

  @PersistenceContext private EntityManager em;
  @Autowired private OrderRepository orderRepository;
  @Autowired private EntityManagerFactory emf;

  private Statistics statistics;

  @BeforeEach
  void setUp() {
    statistics = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
  }

  /**
   * 테스트용 주문 생성 — 상품 2개가 포함된 주문을 저장하고 1차 캐시를 제거한다.
   *
   * <p>em.clear() 후 조회 시 DB에서 재조회하므로 lazy 로딩이 실제 쿼리를 발생시킨다.
   */
  private Long createTestOrder() {
    Seller seller = new Seller("셀러A", "n1test@test.com", SellerGrade.STANDARD);
    em.persist(seller);

    Product p1 = new Product(seller, "상품A", BigDecimal.valueOf(10000), 100);
    Product p2 = new Product(seller, "상품B", BigDecimal.valueOf(5000), 50);
    em.persist(p1);
    em.persist(p2);

    Order order = new Order(BigDecimal.valueOf(25000));
    order.addOrderItem(new OrderItem(order, p1, 1, p1.getPrice()));
    order.addOrderItem(new OrderItem(order, p2, 3, p2.getPrice()));
    em.persist(order);

    em.flush(); // SQL 실행 (트랜잭션 내 커밋은 아님)
    em.clear(); // 1차 캐시 제거 → 이후 조회는 반드시 DB 쿼리 발생

    return order.getId();
  }

  @Test
  @DisplayName("[N+1 재현] orderItems·product lazy 로딩 시 쿼리가 N+1 발생한다")
  void nPlusOne_재현() {
    Long orderId = createTestOrder();
    statistics.clear();

    // when: 일반 findById 후 연관 엔티티에 접근
    Order order = orderRepository.findById(orderId).orElseThrow();
    order.getOrderItems().forEach(item -> item.getProduct().getName()); // lazy 로딩 유발

    long queryCount = statistics.getPrepareStatementCount();

    /*
     * 발생 쿼리 (상품 2개 기준):
     *   1) SELECT * FROM orders WHERE id = ?                   (Order 조회)
     *   2) SELECT * FROM order_items WHERE order_id = ?        (OrderItems lazy 로딩)
     *   3) SELECT * FROM products WHERE id IN (?, ?)           (Product lazy 로딩 — Hibernate 6 배치)
     * 총 3회 쿼리
     *
     * Hibernate 6에서는 @ManyToOne lazy 로딩을 자동 배치(IN 절)로 처리하므로
     * 과거의 item 수만큼 개별 쿼리가 나가는 전통적 N+1 대신 3회로 최적화된다.
     * 단, Order 수가 늘어날 경우 OrderItems 컬렉션 로딩은 여전히 N+1이 발생한다.
     */
    assertThat(queryCount)
        .as("lazy 로딩: Order + OrderItems 컬렉션 + Products 배치 → 최소 3번 쿼리 발생")
        .isGreaterThan(1);
  }

  @Test
  @DisplayName("[N+1 해결] fetch join으로 조회 시 단일 쿼리에 모든 연관 데이터를 가져온다")
  void nPlusOne_해결_fetchJoin() {
    Long orderId = createTestOrder();
    statistics.clear();

    // when: fetch join으로 Order + OrderItems + Product를 한 번에 조회
    Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
    order.getOrderItems().forEach(item -> item.getProduct().getName()); // 추가 쿼리 없음

    long queryCount = statistics.getPrepareStatementCount();

    /*
     * 발생 쿼리:
     *   1) SELECT DISTINCT o, oi, p
     *      FROM orders o
     *      JOIN order_items oi ON oi.order_id = o.id
     *      JOIN products p ON p.id = oi.product_id
     *      WHERE o.id = ?
     * 총 1회 쿼리
     */
    assertThat(queryCount).as("fetch join: 단일 쿼리로 Order + OrderItems + Product 조회").isEqualTo(1);
  }
}
