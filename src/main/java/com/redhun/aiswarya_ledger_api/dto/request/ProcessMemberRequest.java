package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

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
    private Boolean isUpdate = false;
    private List<SpecialLoanRepaymentRequest> specialLoanRepayments;

    @Data
    public static class SpecialLoanRepaymentRequest {
        private Long specialLoanTypeId;
        private BigDecimal amount;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;
}
