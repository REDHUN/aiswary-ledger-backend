package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPersonalReportDto {
    private Long memberId;
    private String memberNumber;
    private String fullName;
    private String yearMonth;
    private List<String> availableMonths;

    private BigDecimal monthInterest;
    private BigDecimal startMonthRemainingLoanBalance;
    private BigDecimal monthEndRemainingLoanBalance;

    private BigDecimal totalDeposits;
    private BigDecimal totalLoanRepaid;
    private BigDecimal totalSpecialLoanRepaid;
    private BigDecimal currentLoanBalance;
    private BigDecimal currentDepositBalance;
    private BigDecimal monthEndDepositBalance;
    private BigDecimal totalMonthlyContributions;
    private BigDecimal totalFinesPaid;
    private BigDecimal totalFinancialAidReceived;
    private BigDecimal totalPaidInPeriod;

    private List<MemberMeetingPaymentEntryDto> meetingPayments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberMeetingPaymentEntryDto {
        private Long meetingId;
        private Integer meetingNumber;
        private String meetingDate;
        private BigDecimal loanRepayment;
        private BigDecimal specialLoanRepayment;
        private String specialLoanTypeName;
        private BigDecimal depositAddition;
        private BigDecimal finePayment;
        private BigDecimal contributionAddition;
        private BigDecimal financialAidPayment;
        private BigDecimal totalPaid;
    }
}
