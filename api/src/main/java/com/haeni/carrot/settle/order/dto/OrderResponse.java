package com.haeni.carrot.settle.order.dto;

import com.haeni.carrot.settle.domain.order.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    OrderStatus status,
    Long totalAmount,
    List<OrderItemResponse> items,
    LocalDateTime createdAt) {

  public static OrderResponse from(OrderResponseDto dto) {
    return new OrderResponse(
        dto.id(),
        dto.status(),
        dto.totalAmount(),
        dto.items().stream().map(OrderItemResponse::from).toList(),
        dto.createdAt());
  }
}