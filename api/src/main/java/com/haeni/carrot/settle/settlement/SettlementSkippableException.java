package com.haeni.carrot.settle.settlement;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 배치에서 의도적으로 skip 처리해야 하는 도메인 예외. {@code SettlementBatchConfig}의 {@code
 * .skip(...)} 정책에 등록되어 fault-tolerant 흐름으로 흘러간다.
 *
 * <p>{@link IllegalStateException}으로 skip하던 기존 구조는 JPA/Hibernate 등 라이브러리 내부 예외까지
 * 묵음 skip되는 위험이 있어 이 전용 예외로 좁혔다. 도메인이 명시적으로 "스킵 가능"이라고 선언한 케이스만 skip된다.
 */
@Getter
public class SettlementSkippableException extends RuntimeException {

  private final SkipReason reason;
  private final Long settlementId;
  private final BigDecimal netAmount;

  public SettlementSkippableException(
      SkipReason reason, Long settlementId, BigDecimal netAmount) {
    super("[" + reason.name() + "] settlementId=" + settlementId + ", netAmount=" + netAmount);
    this.reason = reason;
    this.settlementId = settlementId;
    this.netAmount = netAmount;
  }

  @Getter
  @RequiredArgsConstructor
  public enum SkipReason {
    NEGATIVE_AMOUNT("음수 정산금");

    private final String description;
  }
}