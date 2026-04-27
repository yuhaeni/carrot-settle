package com.haeni.carrot.settle.domain.settlement;

import com.haeni.carrot.settle.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "settlement_skip_logs",
    indexes = {
      @Index(name = "idx_skip_log_settlement_id", columnList = "settlement_id"),
      @Index(name = "idx_skip_log_occurred_at", columnList = "occurred_at")
    })
public class SettlementSkipLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long settlementId;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal netAmount;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(nullable = false)
  private LocalDateTime occurredAt;

  public SettlementSkipLog(
      Long settlementId,
      String status,
      BigDecimal netAmount,
      String reason,
      LocalDateTime occurredAt) {
    this.settlementId = settlementId;
    this.status = status;
    this.netAmount = netAmount;
    this.reason = reason;
    this.occurredAt = occurredAt;
  }
}
