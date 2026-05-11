package com.haeni.carrot.settle.admin;

import com.haeni.carrot.settle.admin.dto.BatchExecutionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/batch-jobs")
@RequiredArgsConstructor
public class BatchAdminController {

  private final BatchAdminService batchAdminService;

  @GetMapping("/executions/{jobExecutionId}")
  public ResponseEntity<BatchExecutionResponse> getExecution(
      @PathVariable Long jobExecutionId) {
    return ResponseEntity.ok(
        BatchExecutionResponse.from(batchAdminService.getJobExecution(jobExecutionId)));
  }
}
