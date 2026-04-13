package com.haeni.carrot.settle.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
    @NotNull(message = "상품 ID는 필수입니다.") Long productId,
    @Positive(message = "수량은 1 이상이어야 합니다.") int quantity) {}