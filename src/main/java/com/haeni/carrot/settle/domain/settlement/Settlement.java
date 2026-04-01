package com.haeni.carrot.settle.domain.settlement;

import com.haeni.carrot.settle.domain.common.BaseEntity;
import com.haeni.carrot.settle.domain.seller.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "settlements",
    indexes = {
      @Index(
          name = "idx_settlement_seller_date_status",
          columnList = "seller_id, settlement_date, status")
    })
public class Settlement extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private Seller seller;

  @Column(nullable = false)
  private LocalDate settlementDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SettlementStatus status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal pgFee;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal platformFee;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal netAmount;

  @Version private Long version;

  protected Settlement() {}

  public Settlement(
      Seller seller,
      LocalDate settlementDate,
      BigDecimal totalAmount,
      BigDecimal pgFee,
      BigDecimal platformFee,
      BigDecimal netAmount) {
    this.seller = seller;
    this.settlementDate = settlementDate;
    this.status = SettlementStatus.PENDING;
    this.totalAmount = totalAmount;
    this.pgFee = pgFee;
    this.platformFee = platformFee;
    this.netAmount = netAmount;
  }

  public void complete() {
    this.status = SettlementStatus.COMPLETED;
  }

  public void fail() {
    this.status = SettlementStatus.FAILED;
  }

  public Long getId() {
    return id;
  }

  public Seller getSeller() {
    return seller;
  }

  public LocalDate getSettlementDate() {
    return settlementDate;
  }

  public SettlementStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public BigDecimal getPgFee() {
    return pgFee;
  }

  public BigDecimal getPlatformFee() {
    return platformFee;
  }

  public BigDecimal getNetAmount() {
    return netAmount;
  }
}
