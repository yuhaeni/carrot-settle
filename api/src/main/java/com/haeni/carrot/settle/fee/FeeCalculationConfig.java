package com.haeni.carrot.settle.fee;

import com.haeni.carrot.settle.domain.fee.PgFeeCalculator;
import com.haeni.carrot.settle.domain.fee.PlatformFeeCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeeCalculationConfig {

  @Bean
  public PgFeeCalculator pgFeeCalculator() {
    return new PgFeeCalculator();
  }

  @Bean
  public PlatformFeeCalculator platformFeeCalculator() {
    return new PlatformFeeCalculator();
  }
}
