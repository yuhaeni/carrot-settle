package com.haeni.carrot.settle.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
    @NotEmpty(message = "주문 상품은 1개 이상이어야 합니다.") @Valid List<OrderItemRequest> items) {}