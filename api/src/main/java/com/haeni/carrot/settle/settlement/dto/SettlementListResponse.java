package com.haeni.carrot.settle.settlement.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record SettlementListResponse(
    List<SettlementResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static SettlementListResponse from(Page<SettlementResponseDto> page) {
    return new SettlementListResponse(
        page.getContent().stream().map(SettlementResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}