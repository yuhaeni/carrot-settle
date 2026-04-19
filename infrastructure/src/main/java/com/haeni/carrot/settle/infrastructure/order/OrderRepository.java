package com.haeni.carrot.settle.infrastructure.order;

import com.haeni.carrot.settle.domain.order.Order;
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
}
