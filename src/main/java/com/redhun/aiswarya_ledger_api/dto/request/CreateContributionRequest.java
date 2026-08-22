package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateContributionRequest {

    @NotNull(message = "Contribution amount is required")
    @DecimalMin(value = "0.01", message = "Contribution amount must be greater than zero")
    private BigDecimal amount;

    private Long meetingId;
    private String description;
}
