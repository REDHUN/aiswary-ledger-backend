package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyLedgerReportDto {
    private String yearMonth;
    private List<String> availableMonths;
    private List<LocalDate> meetingDates;
    private List<MemberLedgerRowDto> memberRows;
    private Map<String, BigDecimal> meetingTotals;
    private BigDecimal grandTotalCollected;
    private BigDecimal totalSpecialLoanRepayments;
    private BigDecimal totalGroupExpenses;
    private BigDecimal surplusAmount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberLedgerRowDto {
        private Long memberId;
        private String memberNumber;
        private String fullName;
        private Map<String, BigDecimal> meetingCollections;
        private BigDecimal totalMonthlyCollected;
        private BigDecimal monthlyContributionSum;
        private BigDecimal depositSum;
        private BigDecimal loanRepaymentSum;
        private BigDecimal specialLoanRepaymentSum;
        private BigDecimal fineSum;
        private BigDecimal currentLoanBalance;
        private BigDecimal currentDepositBalance;
    }
}
