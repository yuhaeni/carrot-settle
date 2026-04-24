package com.haeni.carrot.settle.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.haeni.carrot.settle.domain.fee.FeeDetail;
import com.haeni.carrot.settle.domain.fee.PgFeeCalculator;
import com.haeni.carrot.settle.domain.fee.PlatformFeeCalculator;
import com.haeni.carrot.settle.domain.seller.SellerGrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FeeCalculationServiceTest {

  private final FeeCalculationService service =
      new FeeCalculationService(new PgFeeCalculator(), new PlatformFeeCalculator());

  @Test
  @DisplayName("STANDARD 등급 10000원: PG 300, 플랫폼 500, 총 800")
  void calculate_STANDARD() {
    FeeDetail fee = service.calculate(new BigDecimal("10000"), SellerGrade.STANDARD);

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("500");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("800");
  }

  @Test
  @DisplayName("PREMIUM 등급 10000원: PG 300, 플랫폼 300, 총 600")
  void calculate_PREMIUM() {
    FeeDetail fee = service.calculate(new BigDecimal("10000"), SellerGrade.PREMIUM);

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("300");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("600");
  }

  @Test
  @DisplayName("VIP 등급 10000원: PG 300, 플랫폼 100, 총 400")
  void calculate_VIP() {
    FeeDetail fee = service.calculate(new BigDecimal("10000"), SellerGrade.VIP);

    assertThat(fee.getPgFee()).isEqualByComparingTo("300");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("100");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("400");
  }

  @Test
  @DisplayName("totalFee = pgFee + platformFee 합산이 정확하다")
  void calculate_totalFee는_pgFee_plus_platformFee() {
    FeeDetail fee = service.calculate(new BigDecimal("999"), SellerGrade.STANDARD);

    // 999 * 0.03 = 29.97 → 30, 999 * 0.05 = 49.95 → 50, total = 80
    assertThat(fee.getTotalFee()).isEqualByComparingTo(fee.getPgFee().add(fee.getPlatformFee()));
  }

  @ParameterizedTest
  @EnumSource(SellerGrade.class)
  @DisplayName("0원 주문은 모든 등급에서 모든 수수료가 0원")
  void calculate_0원_경계(SellerGrade grade) {
    FeeDetail fee = service.calculate(BigDecimal.ZERO, grade);

    assertThat(fee.getPgFee()).isEqualByComparingTo("0");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("0");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("0");
  }

  @ParameterizedTest
  @EnumSource(SellerGrade.class)
  @DisplayName("1원 주문은 반올림으로 모든 수수료가 0원")
  void calculate_1원_경계(SellerGrade grade) {
    // 1 * 0.03 = 0.03 / 1 * 0.05 = 0.05 / 1 * 0.01 = 0.01 → 모두 HALF_UP → 0
    FeeDetail fee = service.calculate(new BigDecimal("1"), grade);

    assertThat(fee.getPgFee()).isEqualByComparingTo("0");
    assertThat(fee.getPlatformFee()).isEqualByComparingTo("0");
    assertThat(fee.getTotalFee()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("동일 입력 반복 호출 시 결과는 항상 같다 (멱등성)")
  void calculate_멱등성() {
    BigDecimal amount = new BigDecimal("12345");

    FeeDetail first = service.calculate(amount, SellerGrade.STANDARD);
    FeeDetail second = service.calculate(amount, SellerGrade.STANDARD);
    FeeDetail third = service.calculate(amount, SellerGrade.STANDARD);

    assertThat(first.getPgFee()).isEqualByComparingTo(second.getPgFee());
    assertThat(first.getPgFee()).isEqualByComparingTo(third.getPgFee());
    assertThat(first.getPlatformFee()).isEqualByComparingTo(second.getPlatformFee());
    assertThat(first.getTotalFee()).isEqualByComparingTo(third.getTotalFee());
  }

  @Test
  @DisplayName("반올림 경계가 없는 경우 단건 합산과 일괄 계산 결과가 일치한다")
  void calculate_단건합산_vs_일괄계산_반올림없음() {
    // 10000원 5건 vs 50000원 1건 — 각 계산이 정수로 떨어지는 케이스
    BigDecimal single = new BigDecimal("10000");
    int count = 5;
    BigDecimal bulk = single.multiply(BigDecimal.valueOf(count));

    BigDecimal pgSum = BigDecimal.ZERO;
    BigDecimal platformSum = BigDecimal.ZERO;
    for (int i = 0; i < count; i++) {
      FeeDetail unit = service.calculate(single, SellerGrade.STANDARD);
      pgSum = pgSum.add(unit.getPgFee());
      platformSum = platformSum.add(unit.getPlatformFee());
    }

    FeeDetail bulkFee = service.calculate(bulk, SellerGrade.STANDARD);

    assertThat(pgSum).isEqualByComparingTo(bulkFee.getPgFee());
    assertThat(platformSum).isEqualByComparingTo(bulkFee.getPlatformFee());
  }

  @Test
  @DisplayName("999원 10건 단건 합산과 9990원 일괄 계산 결과가 일치한다")
  void calculate_단건합산_vs_일괄계산_반올림포함() {
    // 단건: 999 * 0.03 = 29.97 → 30. 10건이면 300
    // 일괄: 9990 * 0.03 = 299.7 → 300 → 일치
    // 단건: 999 * 0.05 = 49.95 → 50. 10건이면 500
    // 일괄: 9990 * 0.05 = 499.5 → 500 → 일치
    BigDecimal single = new BigDecimal("999");
    int count = 10;
    BigDecimal bulk = single.multiply(BigDecimal.valueOf(count));

    BigDecimal pgSum = BigDecimal.ZERO;
    BigDecimal platformSum = BigDecimal.ZERO;
    for (int i = 0; i < count; i++) {
      FeeDetail unit = service.calculate(single, SellerGrade.STANDARD);
      pgSum = pgSum.add(unit.getPgFee());
      platformSum = platformSum.add(unit.getPlatformFee());
    }

    FeeDetail bulkFee = service.calculate(bulk, SellerGrade.STANDARD);

    assertThat(pgSum).isEqualByComparingTo(bulkFee.getPgFee());
    assertThat(platformSum).isEqualByComparingTo(bulkFee.getPlatformFee());
  }
}
