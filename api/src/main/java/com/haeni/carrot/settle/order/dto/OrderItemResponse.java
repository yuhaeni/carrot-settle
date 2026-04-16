package com.haeni.carrot.settle.order.dto;

import com.haeni.carrot.settle.domain.order.OrderItem;
import java.math.RoundingMode;

public record OrderItemResponse(Long productId, int quantity, Long unitPrice, Long subtotal) {

  public static OrderItemResponse from(OrderItem item) {
    return new OrderItemResponse(
        item.getProduct().getId(),
        item.getQuantity(),
        item.getUnitPrice().setScale(0, RoundingMode.HALF_UP).longValue(),
        item.getSubtotal().setScale(0, RoundingMode.HALF_UP).longValue());
  }
}