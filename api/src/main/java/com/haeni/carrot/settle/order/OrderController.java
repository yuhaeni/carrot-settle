package com.haeni.carrot.settle.order;

import com.haeni.carrot.settle.common.response.ErrorResponse;
import com.haeni.carrot.settle.common.response.HttpStatusCode;
import com.haeni.carrot.settle.order.dto.CreateOrderRequest;
import com.haeni.carrot.settle.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "주문 API")
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  @Operation(summary = "주문 생성", description = "새 주문을 생성합니다. 생성 즉시 PAID 상태로 전환됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = HttpStatusCode.CREATED, description = "주문 생성 성공"),
    @ApiResponse(
        responseCode = HttpStatusCode.BAD_REQUEST,
        description = "잘못된 요청 (존재하지 않는 상품 또는 유효하지 않은 입력)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(orderService.createOrder(request)));
  }

  @PatchMapping("/{id}/confirm")
  @Operation(summary = "구매 확정", description = "PAID 상태의 주문을 CONFIRMED로 전환하고 Settlement를 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = HttpStatusCode.OK, description = "구매 확정 성공"),
    @ApiResponse(
        responseCode = HttpStatusCode.BAD_REQUEST,
        description = "잘못된 주문 상태 (PAID가 아닌 경우)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = HttpStatusCode.NOT_FOUND,
        description = "존재하지 않는 주문",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<OrderResponse> confirmOrder(@PathVariable Long id) {
    return ResponseEntity.ok(OrderResponse.from(orderService.confirmOrder(id)));
  }

  @PatchMapping("/{id}/refund")
  @Operation(summary = "환불 처리", description = "PAID 상태의 주문을 REFUNDED로 전환합니다. 정산 대상에서 제외됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = HttpStatusCode.OK, description = "환불 처리 성공"),
    @ApiResponse(
        responseCode = HttpStatusCode.BAD_REQUEST,
        description = "잘못된 주문 상태 (PAID가 아닌 경우)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = HttpStatusCode.NOT_FOUND,
        description = "존재하지 않는 주문",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<OrderResponse> refundOrder(@PathVariable Long id) {
    return ResponseEntity.ok(OrderResponse.from(orderService.refundOrder(id)));
  }
}
