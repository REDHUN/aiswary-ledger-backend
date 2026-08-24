package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class IssueGroupLoanRequest {

    private Long groupId;

    @NotEmpty(message = "At least one member must be selected for the group loan")
    private List<Long> memberIds;

    private Long specialLoanTypeId;

    @NotNull(message = "Total loan amount is required")
    @DecimalMin(value = "0.01", message = "Total loan amount must be greater than zero")
    private BigDecimal totalAmount;

    private String notes;

    private LocalDate transactionDate;
}
