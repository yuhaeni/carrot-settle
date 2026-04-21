package com.haeni.carrot.settle.infrastructure.order;

import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

  /** OrderItem과 Product를 fetch join으로 함께 조회한다. N+1 문제를 방지한다. */
  @Query(
      "SELECT DISTINCT o FROM Order o"
          + " JOIN FETCH o.orderItems oi"
          + " JOIN FETCH oi.product"
          + " WHERE o.id = :id")
  Optional<Order> findByIdWithItems(@Param("id") Long id);

  /** OrderItem, Product, Seller까지 fetch join으로 함께 조회한다. 구매 확정 시 Settlement 생성에 사용한다. */
  @Query(
      "SELECT DISTINCT o FROM Order o"
          + " JOIN FETCH o.orderItems oi"
          + " JOIN FETCH oi.product p"
          + " JOIN FETCH p.seller"
          + " WHERE o.id = :id")
  Optional<Order> findByIdWithItemsAndSeller(@Param("id") Long id);

  /** 자동 구매 확정 대상: PAID 상태이고 생성일이 threshold 이전인 주문 ID 목록을 반환한다. */
  @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.createdAt < :threshold")
  List<Long> findIdsByStatusAndCreatedAtBefore(
      @Param("status") OrderStatus status, @Param("threshold") LocalDateTime threshold);
}
