package com.haeni.carrot.settle.domain.settlement;

import com.haeni.carrot.settle.domain.common.BaseEntity;
import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.seller.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(
    name = "settlements",
    indexes = {
      @Index(
          name = "idx_settlement_seller_date_status",
          columnList = "seller_id, settlement_date, status")
    })
@NamedQuery(
    name = Settlement.QUERY_FIND_INCOMPLETED_BEFORE,
    query =
        "SELECT s FROM Settlement s "
            + "WHERE s.status = :status AND s.settlementDate < :targetDate "
            + "ORDER BY s.id")
public class Settlement extends BaseEntity {

  public static final String QUERY_FIND_INCOMPLETED_BEFORE = "Settlement.findIncompletedBefore";

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

  @Embedded private FeeDetail feeDetail;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal netAmount;

  @Version private Long version;

  public Settlement(
      Seller seller,
      LocalDate settlementDate,
      BigDecimal totalAmount,
      FeeDetail feeDetail,
      BigDecimal netAmount) {
    this.seller = seller;
    this.settlementDate = settlementDate;
    this.status = SettlementStatus.INCOMPLETED;
    this.totalAmount = totalAmount;
    this.feeDetail = feeDetail;
    this.netAmount = netAmount;
  }

  public void complete() {
    this.status = SettlementStatus.COMPLETED;
  }
}
