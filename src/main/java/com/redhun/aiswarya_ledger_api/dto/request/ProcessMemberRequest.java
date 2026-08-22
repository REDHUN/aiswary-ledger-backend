package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProcessMemberRequest {

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal loanRepayment = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal interestPayment = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal depositAddition = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal finePayment = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal financialAidPayment = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal monthlyContributionAddition = BigDecimal.ZERO;

    private String notes;
}
