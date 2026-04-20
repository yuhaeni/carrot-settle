package com.haeni.carrot.settle.order;

import com.haeni.carrot.settle.common.exception.BusinessException;
import com.haeni.carrot.settle.common.exception.ErrorCode;
import com.haeni.carrot.settle.domain.order.Order;
import com.haeni.carrot.settle.domain.order.OrderItem;
import com.haeni.carrot.settle.domain.product.Product;
import com.haeni.carrot.settle.domain.seller.Seller;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.infrastructure.order.OrderRepository;
import com.haeni.carrot.settle.infrastructure.product.ProductRepository;
import com.haeni.carrot.settle.infrastructure.settlement.SettlementRepository;
import com.haeni.carrot.settle.order.dto.CreateOrderRequest;
import com.haeni.carrot.settle.order.dto.OrderItemRequest;
import com.haeni.carrot.settle.order.dto.OrderResponseDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

  private static final BigDecimal PG_FEE_RATE = new BigDecimal("0.03");

  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final SettlementRepository settlementRepository;

  @Transactional
  public OrderResponseDto createOrder(CreateOrderRequest request) {
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

    return OrderResponseDto.from(orderRepository.save(order));
  }

  @Transactional
  public OrderResponseDto confirmOrder(Long orderId) {
    Order order =
        orderRepository
            .findByIdWithItemsAndSeller(orderId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

    try {
      order.confirm();
    } catch (IllegalStateException e) {
      throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, HttpStatus.BAD_REQUEST);
    }

    settlementRepository.saveAll(buildSettlements(order));

    return OrderResponseDto.from(order);
  }

  private List<Settlement> buildSettlements(Order order) {
    Map<Seller, BigDecimal> amountBySeller = new LinkedHashMap<>();
    for (OrderItem item : order.getOrderItems()) {
      Seller seller = item.getProduct().getSeller();
      amountBySeller.merge(seller, item.getSubtotal(), BigDecimal::add);
    }

    LocalDate today = LocalDate.now();
    List<Settlement> settlements = new ArrayList<>();
    for (Map.Entry<Seller, BigDecimal> entry : amountBySeller.entrySet()) {
      Seller seller = entry.getKey();
      BigDecimal totalAmount = entry.getValue();
      BigDecimal pgFee = totalAmount.multiply(PG_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
      BigDecimal platformFee =
          totalAmount
              .multiply(seller.getGrade().getPlatformFeeRate())
              .setScale(2, RoundingMode.HALF_UP);
      BigDecimal netAmount = totalAmount.subtract(pgFee).subtract(platformFee);
      settlements.add(new Settlement(seller, today, totalAmount, pgFee, platformFee, netAmount));
    }
    return settlements;
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
