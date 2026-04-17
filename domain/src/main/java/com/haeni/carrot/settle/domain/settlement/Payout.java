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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
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

  @Column(updatable = false)
  private LocalDateTime createdAt;

  public Payout(Settlement settlement, BigDecimal amount) {
    this.settlement = settlement;
    this.amount = amount;
    this.status = PayoutStatus.PENDING;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
