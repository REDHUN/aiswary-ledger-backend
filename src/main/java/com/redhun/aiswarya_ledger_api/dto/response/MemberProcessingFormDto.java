package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProcessingFormDto {

    private Long memberId;
    private String memberNumber;
    private String fullName;

    // Remaining or Current balances for each category
    private BigDecimal loanRemaining;
    private BigDecimal interestRemaining;
    private BigDecimal calculatedInterestAmount;
    private BigDecimal depositCurrent;
    private BigDecimal fineRemaining;
    private BigDecimal financialAidRemaining;
    private BigDecimal monthlyContributionCurrent;

    // Flags & status
    private MemberProcessingStatus processingStatus;
    private Boolean interestCalculationRequired;
    private Boolean interestCalculated;
    private Boolean processingAllowed;
    private String activeInterestPeriod;

    // Last processed payment values (if completed)
    private BigDecimal lastLoanRepayment;
    private BigDecimal lastDepositAddition;
    private BigDecimal lastFinePayment;
    private BigDecimal lastFinancialAidPayment;
    private BigDecimal lastMonthlyContributionAddition;
    private String lastNotes;

    private java.util.List<MemberSpecialLoanBalanceDto> specialLoans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberSpecialLoanBalanceDto {
        private Long specialLoanTypeId;
        private String specialLoanTypeName;
        private BigDecimal currentBalance;
        private BigDecimal lastRepaymentAmount;
    }
}
