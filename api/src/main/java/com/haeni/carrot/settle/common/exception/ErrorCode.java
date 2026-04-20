package com.haeni.carrot.settle.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  PRODUCT_NOT_FOUND("존재하지 않는 상품입니다."),
  ORDER_NOT_FOUND("존재하지 않는 주문입니다."),
  INVALID_ORDER_STATUS("현재 주문 상태에서 해당 작업을 수행할 수 없습니다."),
  INVALID_INPUT("요청 값이 올바르지 않습니다.");

  private final String message;
}
