package com.haeni.carrot.settle.order.dto;

import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record OrderResponse(
    Long id,
    OrderStatus status,
    Long totalAmount,
    List<OrderItemResponse> items,
    OffsetDateTime createdAt) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getTotalAmount().longValue(),
        order.getOrderItems().stream().map(OrderItemResponse::from).toList(),
        order.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
  }
}