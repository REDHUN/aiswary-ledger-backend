package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.FinancialTransaction;
import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import com.redhun.aiswarya_ledger_api.domain.entity.MemberAccount;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.dto.response.FinancialReportDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberBalanceReportDto;
import com.redhun.aiswarya_ledger_api.repository.FinancialTransactionRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingMemberRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberAccountRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final MemberRepository memberRepository;
    private final MemberAccountRepository memberAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final MeetingRepository meetingRepository;
    private final com.redhun.aiswarya_ledger_api.repository.MeetingMemberRepository meetingMemberRepository;

    @Transactional(readOnly = true)
    public FinancialReportDto getFinancialSummary() {
        return getPeriodReport(null, null);
    }

    @Transactional(readOnly = true)
    public FinancialReportDto getPeriodReport(LocalDate startDate, LocalDate endDate) {
        long totalMembers = memberRepository.count();
        long activeMembers = memberRepository.findByIsActiveTrue().size();

        Map<AccountType, BigDecimal> accountTotals = memberAccountRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        MemberAccount::getAccountType,
                        Collectors.reducing(BigDecimal.ZERO, MemberAccount::getCurrentBalance, BigDecimal::add)
                ));

        BigDecimal totalLoans = accountTotals.getOrDefault(AccountType.LOAN, BigDecimal.ZERO);
        BigDecimal totalDeposits = accountTotals.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO);
        BigDecimal totalFines = accountTotals.getOrDefault(AccountType.FINE, BigDecimal.ZERO);
        BigDecimal totalFinancialAid = accountTotals.getOrDefault(AccountType.FINANCIAL_AID, BigDecimal.ZERO);
        BigDecimal totalContributions = accountTotals.getOrDefault(AccountType.MONTHLY_CONTRIBUTION, BigDecimal.ZERO);
        BigDecimal totalInterest = accountTotals.getOrDefault(AccountType.INTEREST, BigDecimal.ZERO);

        // Calculate period collections & disbursals from financial_transactions
        List<FinancialTransaction> transactions = financialTransactionRepository.findAll();
        if (startDate != null) {
            ZonedDateTime startDateTime = startDate.atStartOfDay(ZoneId.systemDefault());
            transactions = transactions.stream()
                    .filter(t -> t.getCreatedAt().isAfter(startDateTime) || t.getCreatedAt().isEqual(startDateTime))
                    .toList();
        }

        if (endDate != null) {
            ZonedDateTime endDateTime = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
            transactions = transactions.stream()
                    .filter(t -> t.getCreatedAt().isBefore(endDateTime) || t.getCreatedAt().isEqual(endDateTime))
                    .toList();
        }

        BigDecimal periodCollections = BigDecimal.ZERO;
        BigDecimal periodDisbursals = BigDecimal.ZERO;

        for (FinancialTransaction tx : transactions) {
            if (Boolean.TRUE.equals(tx.getIsReversed())) continue;

            if (tx.getTransactionType() == TransactionType.REPAYMENT || tx.getTransactionType() == TransactionType.ADDITION) {
                periodCollections = periodCollections.add(tx.getAmount());
            } else if (tx.getTransactionType() == TransactionType.LOAN_ISSUED) {
                periodDisbursals = periodDisbursals.add(tx.getAmount());
            }
        }

        return FinancialReportDto.builder()
                .totalMembers(totalMembers)
                .activeMembers(activeMembers)
                .totalOutstandingLoans(totalLoans)
                .totalDeposits(totalDeposits)
                .totalOutstandingFines(totalFines)
                .totalOutstandingFinancialAid(totalFinancialAid)
                .totalMonthlyContributions(totalContributions)
                .totalOutstandingInterest(totalInterest)
                .periodCollections(periodCollections)
                .periodDisbursals(periodDisbursals)
                .totalTransactionsCount(transactions.size())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MemberBalanceReportDto> getMemberBalancesReport() {
        List<Member> members = memberRepository.findAll();
        List<MemberAccount> allAccounts = memberAccountRepository.findAll();

        Map<Long, Map<AccountType, BigDecimal>> memberBalancesMap = new HashMap<>();
        for (MemberAccount acc : allAccounts) {
            memberBalancesMap
                    .computeIfAbsent(acc.getMember().getId(), k -> new EnumMap<>(AccountType.class))
                    .put(acc.getAccountType(), acc.getCurrentBalance());
        }

        List<MemberBalanceReportDto> result = new ArrayList<>();
        for (Member m : members) {
            Map<AccountType, BigDecimal> balances = memberBalancesMap.getOrDefault(m.getId(), Collections.emptyMap());

            MemberBalanceReportDto dto = MemberBalanceReportDto.builder()
                    .memberId(m.getId())
                    .memberNumber(m.getMemberNumber())
                    .fullName(m.getFullName())
                    .phone(m.getPhone())
                    .isActive(m.getIsActive())
                    .loanBalance(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                    .depositBalance(balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO))
                    .fineBalance(balances.getOrDefault(AccountType.FINE, BigDecimal.ZERO))
                    .contributionBalance(balances.getOrDefault(AccountType.MONTHLY_CONTRIBUTION, BigDecimal.ZERO))
                    .financialAidBalance(balances.getOrDefault(AccountType.FINANCIAL_AID, BigDecimal.ZERO))
                    .interestBalance(balances.getOrDefault(AccountType.INTEREST, BigDecimal.ZERO))
                    .build();

            result.add(dto);
        }

        result.sort(Comparator.comparing(MemberBalanceReportDto::getMemberNumber));
        return result;
    }

    @Transactional(readOnly = true)
    public com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto getMeetingReport(Long meetingId) {
        com.redhun.aiswarya_ledger_api.domain.entity.Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException("Meeting", "id", meetingId));

        List<FinancialTransaction> txList = financialTransactionRepository.findByMeetingId(meetingId).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsReversed()))
                .toList();

        BigDecimal totalRepayments = BigDecimal.ZERO;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalFines = BigDecimal.ZERO;
        BigDecimal totalContributions = BigDecimal.ZERO;
        BigDecimal totalFinancialAid = BigDecimal.ZERO;
        BigDecimal totalLoansIssued = BigDecimal.ZERO;

        Map<Long, com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto.MemberMeetingCollectionDto> memberMap = new HashMap<>();

        for (FinancialTransaction tx : txList) {
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            Member m = tx.getMember();

            var memberDto = memberMap.computeIfAbsent(m.getId(), k ->
                    com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto.MemberMeetingCollectionDto.builder()
                            .memberId(m.getId())
                            .memberNumber(m.getMemberNumber())
                            .fullName(m.getFullName())
                            .loanRepayment(BigDecimal.ZERO)
                            .depositAddition(BigDecimal.ZERO)
                            .finePayment(BigDecimal.ZERO)
                            .contributionAddition(BigDecimal.ZERO)
                            .financialAidPayment(BigDecimal.ZERO)
                            .totalMemberCollected(BigDecimal.ZERO)
                            .build()
            );

            if (tx.getAccountType() == AccountType.LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                totalRepayments = totalRepayments.add(amt);
                memberDto.setLoanRepayment(memberDto.getLoanRepayment().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.DEPOSIT && tx.getTransactionType() == TransactionType.ADDITION) {
                totalDeposits = totalDeposits.add(amt);
                memberDto.setDepositAddition(memberDto.getDepositAddition().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.FINE && tx.getTransactionType() == TransactionType.ADDITION) {
                totalFines = totalFines.add(amt);
                memberDto.setFinePayment(memberDto.getFinePayment().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.MONTHLY_CONTRIBUTION && tx.getTransactionType() == TransactionType.ADDITION) {
                totalContributions = totalContributions.add(amt);
                memberDto.setContributionAddition(memberDto.getContributionAddition().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.FINANCIAL_AID) {
                totalFinancialAid = totalFinancialAid.add(amt);
                memberDto.setFinancialAidPayment(memberDto.getFinancialAidPayment().add(amt));
            } else if (tx.getTransactionType() == TransactionType.LOAN_ISSUED) {
                totalLoansIssued = totalLoansIssued.add(amt);
            }
        }

        BigDecimal totalCollected = totalRepayments.add(totalDeposits).add(totalFines).add(totalContributions);
        List<com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto.MemberMeetingCollectionDto> memberCollections = new ArrayList<>(memberMap.values());
        memberCollections.sort(Comparator.comparing(com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto.MemberMeetingCollectionDto::getMemberNumber));

        long totalMembers = meetingMemberRepository.countByMeetingId(meeting.getId());
        long processedMembers = meetingMemberRepository.countByMeetingIdAndProcessingStatus(meeting.getId(), com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus.COMPLETED);

        return com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto.builder()
                .meetingId(meeting.getId())
                .meetingNumber(meeting.getMeetingNumber())
                .meetingDate(meeting.getMeetingDate())
                .interestPeriod(meeting.getInterestPeriod())
                .status(meeting.getStatus() != null ? meeting.getStatus().name() : "SCHEDULED")
                .processedMembers((int) processedMembers)
                .totalMembers((int) totalMembers)
                .totalCollected(totalCollected)
                .totalLoanRepayments(totalRepayments)
                .totalDepositsCollected(totalDeposits)
                .totalFinesCollected(totalFines)
                .totalMonthlyContributions(totalContributions)
                .totalFinancialAid(totalFinancialAid)
                .totalLoansIssued(totalLoansIssued)
                .memberCollections(memberCollections)
                .build();
    }

    @Transactional(readOnly = true)
    public List<com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto> getAllMeetingReports() {
        return meetingRepository.findAllByOrderByMeetingDateDescMeetingNumberDesc().stream()
                .map(m -> getMeetingReport(m.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public com.redhun.aiswarya_ledger_api.dto.response.MonthlyLedgerReportDto getMonthlyLedgerReport(String yearMonth) {
        List<String> availableMonths = meetingRepository.findDistinctInterestPeriods();
        if (availableMonths == null) {
            availableMonths = new ArrayList<>();
        } else {
            availableMonths = new ArrayList<>(availableMonths);
        }

        String currentPeriod = LocalDate.now().toString().substring(0, 7);
        if (!availableMonths.contains(currentPeriod)) {
            availableMonths.add(0, currentPeriod);
        }

        if (yearMonth == null || yearMonth.isEmpty()) {
            yearMonth = availableMonths.isEmpty() ? currentPeriod : availableMonths.get(0);
        }

        List<Meeting> meetings = meetingRepository.findMeetingsInPeriodSorted(yearMonth);
        List<LocalDate> meetingDates = meetings.stream().map(Meeting::getMeetingDate).toList();

        List<Member> members = memberRepository.findAll();
        members.sort(Comparator.comparing(Member::getMemberNumber));

        List<MemberAccount> allAccounts = memberAccountRepository.findAll();
        Map<Long, Map<AccountType, BigDecimal>> accountBalancesMap = new HashMap<>();
        for (MemberAccount acc : allAccounts) {
            accountBalancesMap
                    .computeIfAbsent(acc.getMember().getId(), k -> new EnumMap<>(AccountType.class))
                    .put(acc.getAccountType(), acc.getCurrentBalance());
        }

        Map<String, BigDecimal> meetingTotals = new HashMap<>();
        BigDecimal grandTotalCollected = BigDecimal.ZERO;

        List<com.redhun.aiswarya_ledger_api.dto.response.MonthlyLedgerReportDto.MemberLedgerRowDto> memberRows = new ArrayList<>();

        for (Member m : members) {
            Map<String, BigDecimal> memberMeetingCollections = new HashMap<>();
            BigDecimal totalMonthlyCollected = BigDecimal.ZERO;
            BigDecimal monthlyContributionSum = BigDecimal.ZERO;
            BigDecimal depositSum = BigDecimal.ZERO;
            BigDecimal loanRepaymentSum = BigDecimal.ZERO;
            BigDecimal fineSum = BigDecimal.ZERO;

            for (Meeting mt : meetings) {
                String dateStr = mt.getMeetingDate().toString();
                List<FinancialTransaction> txList = financialTransactionRepository.findByMeetingId(mt.getId()).stream()
                        .filter(t -> t.getMember().getId().equals(m.getId()) && !Boolean.TRUE.equals(t.getIsReversed()))
                        .toList();

                BigDecimal meetingSum = BigDecimal.ZERO;
                for (FinancialTransaction tx : txList) {
                    BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                    if (tx.getTransactionType() == TransactionType.REPAYMENT || tx.getTransactionType() == TransactionType.ADDITION) {
                        meetingSum = meetingSum.add(amt);

                        if (tx.getAccountType() == AccountType.MONTHLY_CONTRIBUTION) {
                            monthlyContributionSum = monthlyContributionSum.add(amt);
                        } else if (tx.getAccountType() == AccountType.DEPOSIT) {
                            depositSum = depositSum.add(amt);
                        } else if (tx.getAccountType() == AccountType.LOAN) {
                            loanRepaymentSum = loanRepaymentSum.add(amt);
                        } else if (tx.getAccountType() == AccountType.FINE) {
                            fineSum = fineSum.add(amt);
                        }
                    }
                }

                memberMeetingCollections.put(dateStr, meetingSum);
                totalMonthlyCollected = totalMonthlyCollected.add(meetingSum);

                BigDecimal currentMeetingTotal = meetingTotals.getOrDefault(dateStr, BigDecimal.ZERO);
                meetingTotals.put(dateStr, currentMeetingTotal.add(meetingSum));
            }

            grandTotalCollected = grandTotalCollected.add(totalMonthlyCollected);

            Map<AccountType, BigDecimal> balances = accountBalancesMap.getOrDefault(m.getId(), Collections.emptyMap());

            memberRows.add(com.redhun.aiswarya_ledger_api.dto.response.MonthlyLedgerReportDto.MemberLedgerRowDto.builder()
                    .memberId(m.getId())
                    .memberNumber(m.getMemberNumber())
                    .fullName(m.getFullName())
                    .meetingCollections(memberMeetingCollections)
                    .totalMonthlyCollected(totalMonthlyCollected)
                    .monthlyContributionSum(monthlyContributionSum)
                    .depositSum(depositSum)
                    .loanRepaymentSum(loanRepaymentSum)
                    .fineSum(fineSum)
                    .currentLoanBalance(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                    .currentDepositBalance(balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO))
                    .build());
        }

        return com.redhun.aiswarya_ledger_api.dto.response.MonthlyLedgerReportDto.builder()
                .yearMonth(yearMonth)
                .availableMonths(availableMonths)
                .meetingDates(meetingDates)
                .memberRows(memberRows)
                .meetingTotals(meetingTotals)
                .grandTotalCollected(grandTotalCollected)
                .build();
    }

    @Transactional(readOnly = true)
    public com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto getMemberPersonalReport(Long memberId, String yearMonth) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        List<String> availableMonths = meetingRepository.findDistinctInterestPeriods();
        if (availableMonths == null) {
            availableMonths = new ArrayList<>();
        } else {
            availableMonths = new ArrayList<>(availableMonths);
        }

        String currentPeriod = LocalDate.now().toString().substring(0, 7);
        if (!availableMonths.contains(currentPeriod)) {
            availableMonths.add(0, currentPeriod);
        }

        if (yearMonth == null || yearMonth.isEmpty()) {
            yearMonth = availableMonths.isEmpty() ? currentPeriod : availableMonths.get(0);
        }

        List<Meeting> meetings = meetingRepository.findMeetingsInPeriodSorted(yearMonth);
        List<com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto.MemberMeetingPaymentEntryDto> paymentEntries = new ArrayList<>();

        BigDecimal totalPaidInPeriod = BigDecimal.ZERO;

        for (Meeting mt : meetings) {
            List<FinancialTransaction> txList = financialTransactionRepository.findByMeetingId(mt.getId()).stream()
                    .filter(t -> t.getMember().getId().equals(memberId) && !Boolean.TRUE.equals(t.getIsReversed()))
                    .toList();

            BigDecimal loanRepay = BigDecimal.ZERO;
            BigDecimal depositAdd = BigDecimal.ZERO;
            BigDecimal finePay = BigDecimal.ZERO;
            BigDecimal contribAdd = BigDecimal.ZERO;
            BigDecimal aidPay = BigDecimal.ZERO;

            for (FinancialTransaction tx : txList) {
                BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                if (tx.getTransactionType() == TransactionType.REPAYMENT || tx.getTransactionType() == TransactionType.ADDITION) {
                    if (tx.getAccountType() == AccountType.LOAN) loanRepay = loanRepay.add(amt);
                    else if (tx.getAccountType() == AccountType.DEPOSIT) depositAdd = depositAdd.add(amt);
                    else if (tx.getAccountType() == AccountType.FINE) finePay = finePay.add(amt);
                    else if (tx.getAccountType() == AccountType.MONTHLY_CONTRIBUTION) contribAdd = contribAdd.add(amt);
                    else if (tx.getAccountType() == AccountType.FINANCIAL_AID) aidPay = aidPay.add(amt);
                }
            }

            BigDecimal meetingTotal = loanRepay.add(depositAdd).add(finePay).add(contribAdd).add(aidPay);
            totalPaidInPeriod = totalPaidInPeriod.add(meetingTotal);

            paymentEntries.add(com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto.MemberMeetingPaymentEntryDto.builder()
                    .meetingId(mt.getId())
                    .meetingNumber(mt.getMeetingNumber())
                    .meetingDate(mt.getMeetingDate().toString())
                    .loanRepayment(loanRepay)
                    .depositAddition(depositAdd)
                    .finePayment(finePay)
                    .contributionAddition(contribAdd)
                    .financialAidPayment(aidPay)
                    .totalPaid(meetingTotal)
                    .build());
        }

        List<MemberAccount> accounts = memberAccountRepository.findByMemberId(memberId);
        Map<AccountType, BigDecimal> balances = new EnumMap<>(AccountType.class);
        for (MemberAccount acc : accounts) {
            balances.put(acc.getAccountType(), acc.getCurrentBalance());
        }

        return com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto.builder()
                .memberId(member.getId())
                .memberNumber(member.getMemberNumber())
                .fullName(member.getFullName())
                .yearMonth(yearMonth)
                .availableMonths(availableMonths)
                .currentLoanBalance(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                .currentDepositBalance(balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO))
                .totalMonthlyContributions(balances.getOrDefault(AccountType.MONTHLY_CONTRIBUTION, BigDecimal.ZERO))
                .totalFinesPaid(balances.getOrDefault(AccountType.FINE, BigDecimal.ZERO))
                .totalFinancialAidReceived(balances.getOrDefault(AccountType.FINANCIAL_AID, BigDecimal.ZERO))
                .totalPaidInPeriod(totalPaidInPeriod)
                .meetingPayments(paymentEntries)
                .build();
    }
}
