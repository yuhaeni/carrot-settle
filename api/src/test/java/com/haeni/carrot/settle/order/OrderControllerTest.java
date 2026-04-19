package com.haeni.carrot.settle.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.haeni.carrot.settle.TestcontainersConfiguration;
import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.common.exception.ErrorCode;
import com.haeni.carrot.settle.domain.order.OrderStatus;
import com.haeni.carrot.settle.order.dto.CreateOrderRequest;
import com.haeni.carrot.settle.order.dto.OrderItemRequest;
import com.haeni.carrot.settle.order.dto.OrderItemResponse;
import com.haeni.carrot.settle.order.dto.OrderResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderControllerTest {

  @Autowired private WebApplicationContext wac;
  @MockitoBean private OrderService orderService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
  }

  @Test
  @DisplayName("유효한 요청으로 주문 생성 시 201과 주문 정보를 반환한다")
  void createOrder_201() throws Exception {
    // given
    OrderResponse mockResponse =
        new OrderResponse(
            1L,
            OrderStatus.PAID,
            20000L,
            List.of(new OrderItemResponse(1L, 2, 10000L, 20000L)),
            LocalDateTime.now());
    given(orderService.createOrder(any())).willReturn(mockResponse);

    CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(1L, 2)));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.totalAmount").value(20000))
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items[0].quantity").value(2));
  }

  @Test
  @DisplayName("items가 비어있으면 400을 반환한다")
  void createOrder_빈items_400() throws Exception {
    // given
    CreateOrderRequest request = new CreateOrderRequest(List.of());

    // when & then
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("존재하지 않는 상품으로 주문 시 400과 에러 코드를 반환한다")
  void createOrder_존재하지않는상품_400() throws Exception {
    // given
    given(orderService.createOrder(any()))
        .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.BAD_REQUEST));

    CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(999L, 1)));

    // when & then
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("존재하지 않는 상품입니다."));
  }

  @Test
  @DisplayName("요청 body가 없으면 400을 반환한다")
  void createOrder_body없음_400() throws Exception {
    mockMvc
        .perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }
}
