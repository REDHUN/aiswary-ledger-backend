package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReportDto {
    private long totalMembers;
    private long activeMembers;

    private BigDecimal totalOutstandingLoans;
    private BigDecimal totalSpecialLoanBalance;
    private BigDecimal totalDeposits;
    private BigDecimal totalOutstandingFines;
    private BigDecimal totalOutstandingFinancialAid;
    private BigDecimal totalMonthlyContributions;
    private BigDecimal totalOutstandingInterest;
    private BigDecimal totalGroupExpenses;
    private BigDecimal surplusAmount;

    private BigDecimal periodCollections;
    private BigDecimal periodDisbursals;
    private long totalTransactionsCount;

    private LocalDate startDate;
    private LocalDate endDate;
}
