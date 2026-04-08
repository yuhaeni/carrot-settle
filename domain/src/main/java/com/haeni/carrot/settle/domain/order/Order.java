package com.haeni.carrot.settle.domain.order;

import com.haeni.carrot.settle.domain.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> orderItems = new ArrayList<>();

  @Version private Long version;

  protected Order() {}

  public Order(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
    this.status = OrderStatus.PAID;
  }

  public void addOrderItem(OrderItem orderItem) {
    orderItems.add(orderItem);
  }

  public void confirm() {
    if (this.status != OrderStatus.PAID) {
      throw new IllegalStateException("PAID 상태인 주문만 구매 확정할 수 있습니다.");
    }
    this.status = OrderStatus.CONFIRMED;
  }

  public void settle() {
    if (this.status != OrderStatus.CONFIRMED) {
      throw new IllegalStateException("CONFIRMED 상태인 주문만 정산할 수 있습니다.");
    }
    this.status = OrderStatus.SETTLED;
  }

  public void refund() {
    if (this.status != OrderStatus.PAID) {
      throw new IllegalStateException("PAID 상태인 주문만 환불할 수 있습니다.");
    }
    this.status = OrderStatus.REFUNDED;
  }

  public Long getId() {
    return id;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public List<OrderItem> getOrderItems() {
    return Collections.unmodifiableList(orderItems);
  }
}
