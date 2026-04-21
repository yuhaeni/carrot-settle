package com.haeni.carrot.settle.order.dto;

import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderStatus;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseDto(
    Long id,
    OrderStatus status,
    Long totalAmount,
    List<OrderItemResponseDto> items,
    LocalDateTime createdAt) {

  public static OrderResponseDto from(Order order) {
    return new OrderResponseDto(
        order.getId(),
        order.getStatus(),
        order.getTotalAmount().setScale(0, RoundingMode.HALF_UP).longValue(),
        order.getOrderItems().stream().map(OrderItemResponseDto::from).toList(),
        order.getCreatedAt());
  }
}