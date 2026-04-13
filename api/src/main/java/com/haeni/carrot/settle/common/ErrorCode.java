package com.haeni.carrot.settle.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  PRODUCT_NOT_FOUND("존재하지 않는 상품입니다."),
  INVALID_INPUT("요청 값이 올바르지 않습니다.");

  private final String message;
}