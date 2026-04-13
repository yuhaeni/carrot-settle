package com.haeni.carrot.settle.order;

import com.haeni.carrot.settle.common.BusinessException;
import com.haeni.carrot.settle.common.ErrorCode;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;

  public OrderResponse createOrder(CreateOrderRequest request) {
    List<Product> products =
        request.items().stream()
            .map(
                item ->
                    productRepository
                        .findById(item.productId())
                        .orElseThrow(
                            () -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.BAD_REQUEST)))
            .toList();

    BigDecimal totalAmount =
        calculateTotalAmount(request.items(), products);

    Order order = new Order(totalAmount);

    for (int i = 0; i < request.items().size(); i++) {
      OrderItemRequest itemRequest = request.items().get(i);
      Product product = products.get(i);
      order.addOrderItem(new OrderItem(order, product, itemRequest.quantity(), product.getPrice()));
    }

    return OrderResponse.from(orderRepository.save(order));
  }

  private BigDecimal calculateTotalAmount(List<OrderItemRequest> items, List<Product> products) {
    BigDecimal total = BigDecimal.ZERO;
    for (int i = 0; i < items.size(); i++) {
      total = total.add(products.get(i).getPrice().multiply(BigDecimal.valueOf(items.get(i).quantity())));
    }
    return total;
  }
}
