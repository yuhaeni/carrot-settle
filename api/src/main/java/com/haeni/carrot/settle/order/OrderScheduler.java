package com.haeni.carrot.settle.order;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

  private final OrderService orderService;

  @Scheduled(cron = "0 0 2 * * *")
  public void autoConfirmExpiredOrders() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(7);
    List<Long> orderIds = orderService.findPaidOrderIdsOlderThan(threshold);

    log.info("자동 구매 확정 시작: 대상 {}건", orderIds.size());

    int successCount = 0;
    for (Long orderId : orderIds) {
      try {
        orderService.confirmOrder(orderId);
        successCount++;
      } catch (Exception e) {
        log.error("자동 구매 확정 실패: orderId={}", orderId, e);
      }
    }

    log.info("자동 구매 확정 완료: 성공 {}건 / 전체 {}건", successCount, orderIds.size());
  }
}
