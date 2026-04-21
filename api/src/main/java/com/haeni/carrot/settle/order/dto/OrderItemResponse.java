package com.haeni.carrot.settle.order.dto;

public record OrderItemResponse(Long productId, int quantity, Long unitPrice, Long subtotal) {

  public static OrderItemResponse from(OrderItemResponseDto dto) {
    return new OrderItemResponse(dto.productId(), dto.quantity(), dto.unitPrice(), dto.subtotal());
  }
}