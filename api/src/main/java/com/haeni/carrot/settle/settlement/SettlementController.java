package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import com.haeni.carrot.settle.settlement.dto.CalculateSettlementRequest;
import com.haeni.carrot.settle.settlement.dto.CalculateSettlementResponse;
import com.haeni.carrot.settle.settlement.dto.SettlementListResponse;
import com.haeni.carrot.settle.settlement.dto.SettlementSearchCondition;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

  private final SettlementBatchService settlementBatchService;
  private final SettlementQueryService settlementQueryService;

  @PostMapping("/calculate")
  public ResponseEntity<CalculateSettlementResponse> triggerCalculation(
      @Valid @RequestBody CalculateSettlementRequest request) {
    return ResponseEntity.ok(
        CalculateSettlementResponse.from(
            settlementBatchService.runSettlementJob(request.targetDate())));
  }

  @GetMapping
  public ResponseEntity<SettlementListResponse> getSettlements(
      @RequestParam(required = false) Long sellerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @RequestParam(required = false) SettlementStatus status,
      @PageableDefault(size = 20) Pageable pageable) {
    SettlementSearchCondition condition =
        new SettlementSearchCondition(sellerId, startDate, endDate, status);
    return ResponseEntity.ok(
        SettlementListResponse.from(settlementQueryService.search(condition, pageable)));
  }
}