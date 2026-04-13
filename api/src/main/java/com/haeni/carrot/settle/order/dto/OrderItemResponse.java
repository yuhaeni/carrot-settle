package com.haeni.carrot.settle.order.dto;

import com.haeni.carrot.settle.domain.order.OrderItem;

public record OrderItemResponse(Long productId, int quantity, Long unitPrice, Long subtotal) {

  public static OrderItemResponse from(OrderItem item) {
    return new OrderItemResponse(
        item.getProduct().getId(),
        item.getQuantity(),
        item.getUnitPrice().longValue(),
        item.getSubtotal().longValue());
  }
}