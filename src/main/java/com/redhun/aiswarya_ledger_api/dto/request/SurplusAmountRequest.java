package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SurplusAmountRequest {

    @NotNull(message = "Surplus amount is required")
    @DecimalMin(value = "0.01", message = "Surplus amount must be greater than zero")
    private BigDecimal surplusAmount;

    private String description;

    /** Optional: if provided, the surplus amount will also be linked to and reflected in this meeting */
    private Long meetingId;
}
