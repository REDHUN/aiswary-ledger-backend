package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingReportDto {
    private Long meetingId;
    private Integer meetingNumber;
    private LocalDate meetingDate;
    private String interestPeriod;
    private String status;
    private int processedMembers;
    private int totalMembers;

    private BigDecimal totalCollected;
    private BigDecimal totalLoanRepayments;
    private BigDecimal totalDepositsCollected;
    private BigDecimal totalFinesCollected;
    private BigDecimal totalMonthlyContributions;
    private BigDecimal totalSpecialLoanRepayments;
    private List<SpecialLoanRegisterItemDto> specialLoanBreakdown;
    private BigDecimal totalFinancialAid;
    private BigDecimal totalLoansIssued;
    private BigDecimal totalGroupExpenses;
    private List<GroupExpenseDto> groupExpensesBreakdown;
    private BigDecimal surplusAmount;

    private List<MemberMeetingCollectionDto> memberCollections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberMeetingCollectionDto {
        private Long memberId;
        private String memberNumber;
        private String fullName;
        private BigDecimal loanRepayment;
        private BigDecimal depositAddition;
        private BigDecimal finePayment;
        private BigDecimal contributionAddition;
        private BigDecimal specialLoanRepayment;
        private BigDecimal financialAidPayment;
        private BigDecimal totalMemberCollected;
    }
}
