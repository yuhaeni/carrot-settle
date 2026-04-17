package com.haeni.carrot.settle.common.response;

/** Swagger @ApiResponse의 responseCode에 사용하기 위한 HTTP 상태 코드 상수. */
public final class HttpStatusCode {

  public static final String OK = "200";
  public static final String CREATED = "201";
  public static final String NO_CONTENT = "204";
  public static final String BAD_REQUEST = "400";
  public static final String UNAUTHORIZED = "401";
  public static final String FORBIDDEN = "403";
  public static final String NOT_FOUND = "404";
  public static final String CONFLICT = "409";
  public static final String INTERNAL_SERVER_ERROR = "500";

  private HttpStatusCode() {}
}