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
public class CategoryReportDto {
    private BigDecimal totalLoansIssued;
    private BigDecimal totalLoansRepaid;
    private BigDecimal totalOutstandingLoanBalance;
    private BigDecimal totalSpecialLoanBalance;
    private List<CategoryMemberItemDto> loanMembers;

    private BigDecimal totalDepositsCollected;
    private List<CategoryMemberItemDto> depositMembers;

    private BigDecimal totalFinancialAidDisbursed;
    private List<FinancialAidHistoryItemDto> financialAidDisbursements;

    private BigDecimal totalContributionsCollected;
    private BigDecimal totalFinesCollected;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryMemberItemDto {
        private Long memberId;
        private String memberNumber;
        private String fullName;
        private String categoryName;
        private BigDecimal balance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialAidHistoryItemDto {
        private Long transactionId;
        private Long memberId;
        private String memberNumber;
        private String fullName;
        private BigDecimal amount;
        private String transactionDate;
        private Long meetingId;
        private String meetingNumber;
        private String notes;
    }
}
