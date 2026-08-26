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
public class CompletedMeetingRegisterDto {

    private Long meetingId;
    private Integer meetingNumber;
    private LocalDate meetingDate;
    private String interestPeriod;
    private BigDecimal totalDepositsCollected;
    private BigDecimal totalLoanRepaymentsCollected;
    private BigDecimal totalFinesCollected;
    private BigDecimal totalMonthlyContributionsCollected;
    private BigDecimal totalSpecialLoanRepaymentsCollected;
    private List<SpecialLoanRegisterItemDto> specialLoanBreakdown;
    private BigDecimal totalLoansIssued;
    private List<LoanIssuedRegisterItemDto> loansIssuedBreakdown;
    private BigDecimal totalFinancialAidDisbursed;
    private BigDecimal totalGroupExpenses;
    private List<GroupExpenseDto> groupExpensesBreakdown;
    private BigDecimal totalGroupProfit;
    private List<GroupProfitDto> groupProfitsBreakdown;
    private BigDecimal totalNetMeetingCollections;
    private BigDecimal surplusAmount;
}
