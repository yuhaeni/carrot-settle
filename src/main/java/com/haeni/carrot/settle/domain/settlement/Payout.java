package com.haeni.carrot.settle.domain.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;

@Entity
@Table(name = "payouts")
public class Payout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "settlement_id", nullable = false)
  private Settlement settlement;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PayoutStatus status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  protected Payout() {}

  public Payout(Settlement settlement, BigDecimal amount) {
    this.settlement = settlement;
    this.amount = amount;
    this.status = PayoutStatus.PENDING;
  }

  public Long getId() {
    return id;
  }

  public Settlement getSettlement() {
    return settlement;
  }

  public PayoutStatus getStatus() {
    return status;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
