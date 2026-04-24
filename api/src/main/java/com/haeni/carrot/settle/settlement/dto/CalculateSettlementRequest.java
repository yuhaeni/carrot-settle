package com.haeni.carrot.settle.settlement.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CalculateSettlementRequest(@NotNull LocalDate targetDate) {}
