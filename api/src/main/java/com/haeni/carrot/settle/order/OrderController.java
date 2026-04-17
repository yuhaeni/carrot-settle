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
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
  }
}
