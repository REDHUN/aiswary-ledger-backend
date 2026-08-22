package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateLoanRequest {

    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "0.01", message = "Loan amount must be greater than zero")
    private BigDecimal amount;

    private Long meetingId;
    private String description;
}
