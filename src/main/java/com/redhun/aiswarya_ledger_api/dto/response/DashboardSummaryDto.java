package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {

    private MeetingDto nextMeeting;
    private CompletedMeetingRegisterDto lastCompletedMeetingRegister;

    // Financial Summaries
    private BigDecimal totalOutstandingLoans;
    private BigDecimal totalDeposits;
    private BigDecimal totalOutstandingFines;
    private BigDecimal totalOutstandingFinancialAid;
    private BigDecimal totalMonthlyContributions;
    private BigDecimal totalOutstandingInterest;
    private BigDecimal currentMeetingCollections;
    private BigDecimal surplusAmount;

    // Interest Calculation Status Summary for current month
    private String currentInterestPeriod;
    private Long interestCalculatedMembersCount;
    private Long interestPendingMembersCount;
}
