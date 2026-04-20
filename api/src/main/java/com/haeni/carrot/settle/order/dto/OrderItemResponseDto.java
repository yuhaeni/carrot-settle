package com.haeni.carrot.settle.order.dto;

import com.haeni.carrot.settle.domain.order.OrderItem;
import java.math.RoundingMode;

public record OrderItemResponseDto(Long productId, int quantity, Long unitPrice, Long subtotal) {

  public static OrderItemResponseDto from(OrderItem item) {
    return new OrderItemResponseDto(
        item.getProduct().getId(),
        item.getQuantity(),
        item.getUnitPrice().setScale(0, RoundingMode.HALF_UP).longValue(),
        item.getSubtotal().setScale(0, RoundingMode.HALF_UP).longValue());
  }
}