package com.haeni.carrot.settle.order;

import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.common.exception.ErrorCode;
import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderItem;
import com.haeni.carrot.settle.domain.product.Product;
import com.haeni.carrot.settle.infrastructure.order.OrderRepository;
import com.haeni.carrot.settle.infrastructure.product.ProductRepository;
import com.haeni.carrot.settle.order.dto.CreateOrderRequest;
import com.haeni.carrot.settle.order.dto.OrderItemRequest;
import com.haeni.carrot.settle.order.dto.OrderResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;

  @Transactional
  public OrderResponse createOrder(CreateOrderRequest request) {
    List<Long> productIds =
        request.items().stream().map(OrderItemRequest::productId).toList();

    Map<Long, Product> productMap =
        productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

    if (productMap.size() != productIds.size()) {
      throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.BAD_REQUEST);
    }

    BigDecimal totalAmount = calculateTotalAmount(request.items(), productMap);

    Order order = new Order(totalAmount);
    for (OrderItemRequest itemRequest : request.items()) {
      Product product = productMap.get(itemRequest.productId());
      order.addOrderItem(new OrderItem(order, product, itemRequest.quantity(), product.getPrice()));
    }

    return OrderResponse.from(orderRepository.save(order));
  }

  private BigDecimal calculateTotalAmount(
      List<OrderItemRequest> items, Map<Long, Product> productMap) {
    BigDecimal total = BigDecimal.ZERO;
    for (OrderItemRequest item : items) {
      total =
          total.add(
              productMap.get(item.productId()).getPrice().multiply(BigDecimal.valueOf(item.quantity())));
    }
    return total;
  }
}
