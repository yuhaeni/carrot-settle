package com.haeni.carrot.settle.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.fee.PgFeeCalculator;
import com.haeni.carrot.settle.domain.fee.PlatformFeeCalculator;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeeCalculationBuilderTest {

  private final PgFeeCalculator pgFeeCalculator = new PgFeeCalculator();
  private final PlatformFeeCalculator platformFeeCalculator = new PlatformFeeCalculator();

  private FeeCalculationBuilder builderFor(BigDecimal amount) {
    return new FeeCalculationBuilder(amount, pgFeeCalculator, platformFeeCalculator);
  }

  @Test
  @DisplayName("withPgFee, withPlatformFee 둘 다 적용하면 각 수수료가 누적된다")
  void build_두_수수료_모두_적용() {
    FeeDetail fee =
        builderFor(new BigDecimal("10000")).withPgFee().withPlatformFee(SellerGrade.STANDARD).build();

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("500");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("800");
  }

  @Test
  @DisplayName("withPgFee만 호출 시 platformFee는 0")
  void build_PG만_적용() {
    FeeDetail fee = builderFor(new BigDecimal("10000")).withPgFee().build();

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("0");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("300");
  }

  @Test
  @DisplayName("withPlatformFee만 호출 시 pgFee는 0")
  void build_플랫폼만_적용() {
    FeeDetail fee = builderFor(new BigDecimal("10000")).withPlatformFee(SellerGrade.VIP).build();

    assertThat(fee.getPgFee()).isEqualByComparingTo("0");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("100");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("100");
  }

  @Test
  @DisplayName("아무 with도 호출하지 않으면 모든 수수료는 0")
  void build_아무것도_호출_안함() {
    FeeDetail fee = builderFor(new BigDecimal("10000")).build();

    assertThat(fee.getPgFee()).isEqualByComparingTo("0");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("0");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("동일 수수료를 여러 번 호출해도 마지막 호출 값이 최종 결과에 반영된다")
  void build_중복_호출은_덮어쓴다() {
    FeeDetail fee =
        builderFor(new BigDecimal("10000"))
            .withPlatformFee(SellerGrade.STANDARD)
            .withPlatformFee(SellerGrade.VIP)
            .build();

    // 마지막 호출인 VIP(1%) 요율 반영
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("100");
  }
}
