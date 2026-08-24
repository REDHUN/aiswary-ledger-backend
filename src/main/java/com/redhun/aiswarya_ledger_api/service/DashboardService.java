package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.domain.entity.MemberAccount;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.InterestStatus;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import com.redhun.aiswarya_ledger_api.dto.response.CompletedMeetingRegisterDto;
import com.redhun.aiswarya_ledger_api.dto.response.DashboardSummaryDto;
import com.redhun.aiswarya_ledger_api.dto.response.MeetingDto;
import com.redhun.aiswarya_ledger_api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MemberAccountRepository memberAccountRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingService meetingService;
    private final FinancialTransactionRepository transactionRepository;
    private final InterestCalculationRepository interestRepository;
    private final MemberRepository memberRepository;
    private final SystemSettingService systemSettingService;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary() {
        // Aggregate totals across member accounts
        List<MemberAccount> allAccounts = memberAccountRepository.findAll();

        BigDecimal totalLoans = BigDecimal.ZERO;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalFines = BigDecimal.ZERO;
        BigDecimal totalAid = BigDecimal.ZERO;
        BigDecimal totalContributions = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (MemberAccount acc : allAccounts) {
            switch (acc.getAccountType()) {
                case LOAN -> totalLoans = totalLoans.add(acc.getCurrentBalance());
                case DEPOSIT -> totalDeposits = totalDeposits.add(acc.getCurrentBalance());
                case FINE -> totalFines = totalFines.add(acc.getCurrentBalance());
                case FINANCIAL_AID -> totalAid = totalAid.add(acc.getCurrentBalance());
                case MONTHLY_CONTRIBUTION -> totalContributions = totalContributions.add(acc.getCurrentBalance());
                case INTEREST -> totalInterest = totalInterest.add(acc.getCurrentBalance());
            }
        }

        // Fetch next scheduled or open meeting
        MeetingDto nextMeetingDto = null;
        Optional<Meeting> openMeeting = meetingRepository.findFirstByStatusOrderByMeetingDateAsc(MeetingStatus.OPEN);
        Optional<Meeting> nextMeeting = openMeeting.isPresent() ? openMeeting : meetingRepository.findFirstByStatusOrderByMeetingDateAsc(MeetingStatus.SCHEDULED);

        BigDecimal currentCollections = BigDecimal.ZERO;
        if (nextMeeting.isPresent()) {
            nextMeetingDto = meetingService.mapToDto(nextMeeting.get());
            BigDecimal collections = transactionRepository.sumTotalMeetingCollections(nextMeeting.get().getId());
            if (collections != null) {
                currentCollections = collections;
            }
        }

        String currentPeriod = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        long calculatedMembersCount = interestRepository.countByInterestPeriod(currentPeriod);
        long totalActiveMembers = memberRepository.findByIsActiveTrue().size();
        long pendingMembersCount = Math.max(0, totalActiveMembers - calculatedMembersCount);

        CompletedMeetingRegisterDto lastRegister = meetingService.getCompletedMeetingRegister(null);

        return DashboardSummaryDto.builder()
                .nextMeeting(nextMeetingDto)
                .lastCompletedMeetingRegister(lastRegister)
                .totalOutstandingLoans(totalLoans)
                .totalDeposits(totalDeposits)
                .totalOutstandingFines(totalFines)
                .totalOutstandingFinancialAid(totalAid)
                .totalMonthlyContributions(totalContributions)
                .totalOutstandingInterest(totalInterest)
                .currentMeetingCollections(currentCollections)
                .surplusAmount(systemSettingService.getSurplusAmount())
                .currentInterestPeriod(currentPeriod)
                .interestCalculatedMembersCount(calculatedMembersCount)
                .interestPendingMembersCount(pendingMembersCount)
                .build();
    }
}
