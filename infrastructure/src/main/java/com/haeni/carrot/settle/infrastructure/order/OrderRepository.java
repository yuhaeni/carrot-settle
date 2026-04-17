package com.haeni.carrot.settle.infrastructure.order;

import com.haeni.carrot.settle.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
