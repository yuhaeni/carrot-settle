package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.settlement.dto.CalculateSettlementRequest;
import com.haeni.carrot.settle.settlement.dto.CalculateSettlementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

  private final SettlementBatchService settlementBatchService;

  @PostMapping("/calculate")
  public ResponseEntity<CalculateSettlementResponse> triggerCalculation(
      @Valid @RequestBody CalculateSettlementRequest request) {
    return ResponseEntity.ok(
        CalculateSettlementResponse.from(
            settlementBatchService.runSettlementJob(request.targetDate())));
  }
}