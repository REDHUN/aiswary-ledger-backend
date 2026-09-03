package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.dto.response.*;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.*;
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
    private final MeetingMemberRepository meetingMemberRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final SystemSettingService systemSettingService;
    private final InterestCalculationRepository interestCalculationRepository;



    @Transactional(readOnly = true)
    public List<MemberBalanceReportDto> getMemberBalancesReport() {
        List<Member> members = memberRepository.findAll();
        List<MemberAccount> allAccounts = memberAccountRepository.findAll();

        Map<Long, Map<AccountType, BigDecimal>> memberBalancesMap = new HashMap<>();
        Map<Long, List<MemberBalanceReportDto.SpecialLoanBalanceItemDto>> memberSpecialLoansMap = new HashMap<>();

        for (MemberAccount acc : allAccounts) {
            memberBalancesMap
                    .computeIfAbsent(acc.getMember().getId(), k -> new EnumMap<>(AccountType.class))
                    .put(acc.getAccountType(), acc.getCurrentBalance());

            if (acc.getAccountType() == AccountType.SPECIAL_LOAN && acc.getCurrentBalance() != null && acc.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0) {
                String typeName = acc.getSpecialLoanType() != null ? acc.getSpecialLoanType().getName() : "Special Loan";
                Long typeId = acc.getSpecialLoanType() != null ? acc.getSpecialLoanType().getId() : null;

                memberSpecialLoansMap.computeIfAbsent(acc.getMember().getId(), k -> new ArrayList<>())
                        .add(MemberBalanceReportDto.SpecialLoanBalanceItemDto.builder()
                                .specialLoanTypeId(typeId)
                                .specialLoanTypeName(typeName)
                                .currentBalance(acc.getCurrentBalance())
                                .build());
            }
        }

        List<MemberBalanceReportDto> result = new ArrayList<>();
        for (Member m : members) {
            Map<AccountType, BigDecimal> balances = memberBalancesMap.getOrDefault(m.getId(), Collections.emptyMap());
            List<MemberBalanceReportDto.SpecialLoanBalanceItemDto> splList = memberSpecialLoansMap.getOrDefault(m.getId(), Collections.emptyList());

            BigDecimal totalSplBal = splList.stream()
                    .map(MemberBalanceReportDto.SpecialLoanBalanceItemDto::getCurrentBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            MemberBalanceReportDto dto = MemberBalanceReportDto.builder()
                    .memberId(m.getId())
                    .memberNumber(m.getMemberNumber())
                    .fullName(m.getFullName())
                    .phone(m.getPhone())
                    .isActive(m.getIsActive())
                    .loanBalance(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                    .specialLoanBalance(totalSplBal)
                    .specialLoanBalances(splList)
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
    public MeetingReportDto getMeetingReport(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        List<FinancialTransaction> txList = financialTransactionRepository.findByMeetingId(meetingId).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsReversed())
                        && !"MEETING_SURPLUS_TRANSFER".equals(t.getReferenceType())
                        && !"SURPLUS_FUND_ADDITION".equals(t.getReferenceType()))
                .toList();

        BigDecimal totalRepayments = BigDecimal.ZERO;
        BigDecimal totalSpecialLoanRepayments = BigDecimal.ZERO;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalFines = BigDecimal.ZERO;
        BigDecimal totalContributions = BigDecimal.ZERO;
        BigDecimal totalFinancialAid = BigDecimal.ZERO;
        BigDecimal totalLoansIssued = BigDecimal.ZERO;

        Map<Long, MeetingReportDto.MemberMeetingCollectionDto> memberMap = new HashMap<>();

        for (FinancialTransaction tx : txList) {


            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            Member m = tx.getMember();
            if (m == null) continue;

            var memberDto = memberMap.computeIfAbsent(m.getId(), k ->
                    MeetingReportDto.MemberMeetingCollectionDto.builder()
                            .memberId(m.getId())
                            .memberNumber(m.getMemberNumber())
                            .fullName(m.getFullName())
                            .loanRepayment(BigDecimal.ZERO)
                            .depositAddition(BigDecimal.ZERO)
                            .finePayment(BigDecimal.ZERO)
                            .contributionAddition(BigDecimal.ZERO)
                            .specialLoanRepayment(BigDecimal.ZERO)
                            .financialAidPayment(BigDecimal.ZERO)
                            .totalMemberCollected(BigDecimal.ZERO)
                            .build()
            );

            if (tx.getAccountType() == AccountType.LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                totalRepayments = totalRepayments.add(amt);
                memberDto.setLoanRepayment(memberDto.getLoanRepayment().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.SPECIAL_LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                totalSpecialLoanRepayments = totalSpecialLoanRepayments.add(amt);
                memberDto.setSpecialLoanRepayment(memberDto.getSpecialLoanRepayment().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.DEPOSIT && (tx.getTransactionType() == TransactionType.ADDITION || tx.getTransactionType() == TransactionType.INITIAL_BALANCE)) {
                totalDeposits = totalDeposits.add(amt);
                memberDto.setDepositAddition(memberDto.getDepositAddition().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.FINE && tx.getTransactionType() == TransactionType.REPAYMENT) {
                totalFines = totalFines.add(amt);
                memberDto.setFinePayment(memberDto.getFinePayment().add(amt));
                memberDto.setTotalMemberCollected(memberDto.getTotalMemberCollected().add(amt));
            } else if (tx.getAccountType() == AccountType.MONTHLY_CONTRIBUTION && (tx.getTransactionType() == TransactionType.ADDITION || tx.getTransactionType() == TransactionType.INITIAL_BALANCE)) {
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

        BigDecimal totalCollected = totalRepayments.add(totalSpecialLoanRepayments).add(totalDeposits).add(totalFines).add(totalContributions);
        List<MeetingReportDto.MemberMeetingCollectionDto> memberCollections = new ArrayList<>(memberMap.values());
        memberCollections.sort(Comparator.comparing(MeetingReportDto.MemberMeetingCollectionDto::getMemberNumber));

        List<Object[]> slRows = financialTransactionRepository.sumMeetingSpecialLoansByType(meetingId);
        List<SpecialLoanRegisterItemDto> slBreakdown = slRows.stream().map(r ->
                SpecialLoanRegisterItemDto.builder()
                        .specialLoanTypeId((Long) r[0])
                        .specialLoanTypeName((String) r[1] != null ? (String) r[1] : "Special Loan")
                        .amount((BigDecimal) r[2])
                        .build()).collect(Collectors.toList());

        BigDecimal groupExpenses = groupExpenseRepository.sumMeetingExpenses(meetingId);
        if (groupExpenses == null) groupExpenses = BigDecimal.ZERO;

        List<GroupExpense> expList = groupExpenseRepository.findByMeetingId(meetingId);
        List<GroupExpenseDto> groupExpensesBreakdown = expList.stream().map(e ->
                GroupExpenseDto.builder()
                        .id(e.getId())
                        .expenseTypeId(e.getExpenseType() != null ? e.getExpenseType().getId() : null)
                        .expenseTypeName(e.getExpenseType() != null ? e.getExpenseType().getName() : "Expense")
                        .amount(e.getAmount())
                        .expenseDate(e.getExpenseDate())
                        .description(e.getDescription())
                        .meetingId(e.getMeeting() != null ? e.getMeeting().getId() : null)
                        .createdAt(e.getCreatedAt())
                        .build()).collect(Collectors.toList());

        BigDecimal surplus = meeting.getSurplusAmount();
        if (surplus == null) {
            surplus = systemSettingService.getSurplusAmount();
        }

        long totalMembers = meetingMemberRepository.countByMeetingId(meeting.getId());
        long processedMembers = meetingMemberRepository.countByMeetingIdAndProcessingStatus(meeting.getId(), com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus.COMPLETED);

        return MeetingReportDto.builder()
                .meetingId(meeting.getId())
                .meetingNumber(meeting.getMeetingNumber())
                .meetingDate(meeting.getMeetingDate())
                .interestPeriod(meeting.getInterestPeriod())
                .status(meeting.getStatus() != null ? meeting.getStatus().name() : "SCHEDULED")
                .processedMembers((int) processedMembers)
                .totalMembers((int) totalMembers)
                .totalCollected(totalCollected)
                .totalLoanRepayments(totalRepayments)
                .totalSpecialLoanRepayments(totalSpecialLoanRepayments)
                .specialLoanBreakdown(slBreakdown)
                .totalDepositsCollected(totalDeposits)
                .totalFinesCollected(totalFines)
                .totalMonthlyContributions(totalContributions)
                .totalFinancialAid(totalFinancialAid)
                .totalLoansIssued(totalLoansIssued)
                .totalGroupExpenses(groupExpenses)
                .groupExpensesBreakdown(groupExpensesBreakdown)
                .surplusAmount(surplus)
                .memberCollections(memberCollections)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MeetingReportDto> getAllMeetingReports() {
        return meetingRepository.findAllByOrderByMeetingDateDescMeetingNumberDesc().stream()
                .map(m -> getMeetingReport(m.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyLedgerReportDto getMonthlyLedgerReport(String yearMonth) {
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
        BigDecimal monthlySpecialLoansTotal = BigDecimal.ZERO;
        BigDecimal monthlyExpensesTotal = BigDecimal.ZERO;

        for (Meeting m : meetings) {
            BigDecimal mExpenses = groupExpenseRepository.sumMeetingExpenses(m.getId());
            if (mExpenses != null) {
                monthlyExpensesTotal = monthlyExpensesTotal.add(mExpenses);
            }
        }

        List<MonthlyLedgerReportDto.MemberLedgerRowDto> memberRows = new ArrayList<>();

        for (Member m : members) {
            Map<String, BigDecimal> memberMeetingCollections = new HashMap<>();
            BigDecimal totalMonthlyCollected = BigDecimal.ZERO;
            BigDecimal monthlyContributionSum = BigDecimal.ZERO;
            BigDecimal depositSum = BigDecimal.ZERO;
            BigDecimal loanRepaymentSum = BigDecimal.ZERO;
            BigDecimal specialLoanRepaymentSum = BigDecimal.ZERO;
            BigDecimal fineSum = BigDecimal.ZERO;

            for (Meeting mt : meetings) {
                String dateStr = mt.getMeetingDate().toString();
                List<FinancialTransaction> txList = financialTransactionRepository.findByMeetingId(mt.getId()).stream()
                        .filter(t -> t.getMember() != null && t.getMember().getId().equals(m.getId()) && !Boolean.TRUE.equals(t.getIsReversed()) && !"MEETING_SURPLUS_TRANSFER".equals(t.getReferenceType()) && !"SURPLUS_FUND_ADDITION".equals(t.getReferenceType()) && !"OPENING_BALANCE".equals(t.getReferenceType()) && !"HISTORICAL_IMPORT".equals(t.getReferenceType()) && !"INITIAL_BALANCE".equals(t.getReferenceType()))
                        .toList();

                BigDecimal meetingSum = BigDecimal.ZERO;
                for (FinancialTransaction tx : txList) {
                    BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                    
                    // Skip loan issuances, reversals, and financial aid disbursements
                    if (tx.getTransactionType() == TransactionType.LOAN_ISSUED || tx.getTransactionType() == TransactionType.REVERSAL) {
                        continue;
                    }
                    if (tx.getAccountType() == AccountType.FINANCIAL_AID) {
                        continue;
                    }

                    if (tx.getAccountType() == AccountType.MONTHLY_CONTRIBUTION) {
                        meetingSum = meetingSum.add(amt);
                        monthlyContributionSum = monthlyContributionSum.add(amt);
                    } else if (tx.getAccountType() == AccountType.DEPOSIT) {
                        meetingSum = meetingSum.add(amt);
                        depositSum = depositSum.add(amt);
                    } else if (tx.getAccountType() == AccountType.FINE) {
                        meetingSum = meetingSum.add(amt);
                        fineSum = fineSum.add(amt);
                    } else if (tx.getAccountType() == AccountType.LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                        meetingSum = meetingSum.add(amt);
                        loanRepaymentSum = loanRepaymentSum.add(amt);
                    } else if (tx.getAccountType() == AccountType.SPECIAL_LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                        meetingSum = meetingSum.add(amt);
                        specialLoanRepaymentSum = specialLoanRepaymentSum.add(amt);
                        monthlySpecialLoansTotal = monthlySpecialLoansTotal.add(amt);
                    }
                }

                memberMeetingCollections.put(dateStr, meetingSum);
                totalMonthlyCollected = totalMonthlyCollected.add(meetingSum);

                meetingTotals.merge(dateStr, meetingSum, BigDecimal::add);
            }

            grandTotalCollected = grandTotalCollected.add(totalMonthlyCollected);

            Map<AccountType, BigDecimal> balances = accountBalancesMap.getOrDefault(m.getId(), Collections.emptyMap());

            MonthlyLedgerReportDto.MemberLedgerRowDto row = MonthlyLedgerReportDto.MemberLedgerRowDto.builder()
                    .memberId(m.getId())
                    .memberNumber(m.getMemberNumber())
                    .fullName(m.getFullName())
                    .meetingCollections(memberMeetingCollections)
                    .totalMonthlyCollected(totalMonthlyCollected)
                    .monthlyContributionSum(monthlyContributionSum)
                    .depositSum(depositSum)
                    .loanRepaymentSum(loanRepaymentSum)
                    .specialLoanRepaymentSum(specialLoanRepaymentSum)
                    .fineSum(fineSum)
                    .currentLoanBalance(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                    .currentDepositBalance(balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO))
                    .build();

            memberRows.add(row);
        }

        BigDecimal surplus = systemSettingService.getSurplusAmount();

        return MonthlyLedgerReportDto.builder()
                .yearMonth(yearMonth)
                .availableMonths(availableMonths)
                .meetingDates(meetings.stream().map(Meeting::getMeetingDate).toList())
                .memberRows(memberRows)
                .meetingTotals(meetingTotals)
                .grandTotalCollected(grandTotalCollected)
                .totalSpecialLoanRepayments(monthlySpecialLoansTotal)
                .totalGroupExpenses(monthlyExpensesTotal)
                .surplusAmount(surplus)
                .build();
    }

    @Transactional(readOnly = true)
    public MemberPersonalReportDto getMemberPersonalReport(Long memberId, String yearMonth) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        List<String> availableMonths = meetingRepository.findDistinctInterestPeriods();
        if (availableMonths == null) availableMonths = new ArrayList<>();
        else availableMonths = new ArrayList<>(availableMonths);

        String currentPeriod = LocalDate.now().toString().substring(0, 7);
        if (!availableMonths.contains(currentPeriod)) {
            availableMonths.add(0, currentPeriod);
        }

        final String finalYearMonth = (yearMonth == null || yearMonth.isEmpty())
                ? (availableMonths.isEmpty() ? currentPeriod : availableMonths.get(0))
                : yearMonth;

        List<MemberAccount> accounts = memberAccountRepository.findByMemberId(memberId);
        Map<AccountType, BigDecimal> balances = accounts.stream()
                .collect(Collectors.toMap(MemberAccount::getAccountType, MemberAccount::getCurrentBalance, (a, b) -> a));

        List<Meeting> meetings = meetingRepository.findMeetingsInPeriodSorted(finalYearMonth);
        List<MemberPersonalReportDto.MemberMeetingPaymentEntryDto> meetingPayments = new ArrayList<>();

        BigDecimal totalPaidInPeriod = BigDecimal.ZERO;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalLoanRepaid = BigDecimal.ZERO;
        BigDecimal totalSpecialLoanRepaid = BigDecimal.ZERO;
        BigDecimal totalMonthlyContributions = BigDecimal.ZERO;
        BigDecimal totalFinesPaid = BigDecimal.ZERO;
        BigDecimal totalFinancialAidReceived = BigDecimal.ZERO;

        for (Meeting m : meetings) {
            List<FinancialTransaction> txList =
                    financialTransactionRepository.findByMeetingId(m.getId())
                            .stream()
                            .filter(t ->
                                    t.getMember() != null
                                            && t.getMember().getId().equals(memberId)
                                            && !Boolean.TRUE.equals(t.getIsReversed())
                                            && !"MEETING_SURPLUS_TRANSFER".equals(t.getReferenceType())
                                            && !"SURPLUS_FUND_ADDITION".equals(t.getReferenceType())
                                            && !"OPENING_BALANCE".equals(t.getReferenceType())
                                            && !"HISTORICAL_IMPORT".equals(t.getReferenceType())
                                            && !"INITIAL_BALANCE".equals(t.getReferenceType())

                            )
                            .toList();

            BigDecimal lRep = BigDecimal.ZERO;
            BigDecimal slRep = BigDecimal.ZERO;
            String slTypeName = null;
            BigDecimal dep = BigDecimal.ZERO;
            BigDecimal fine = BigDecimal.ZERO;
            BigDecimal contrib = BigDecimal.ZERO;
            BigDecimal aid = BigDecimal.ZERO;

            for (FinancialTransaction tx : txList) {
                BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                if (tx.getAccountType() == AccountType.LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                    lRep = lRep.add(amt);
                    totalLoanRepaid = totalLoanRepaid.add(amt);
                } else if (tx.getAccountType() == AccountType.SPECIAL_LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                    slRep = slRep.add(amt);
                    totalSpecialLoanRepaid = totalSpecialLoanRepaid.add(amt);
                    if (tx.getSpecialLoanType() != null) {
                        slTypeName = tx.getSpecialLoanType().getName();
                    }
                } else if (tx.getAccountType() == AccountType.DEPOSIT && (tx.getTransactionType() == TransactionType.ADDITION || tx.getTransactionType() == TransactionType.INITIAL_BALANCE)) {
                    dep = dep.add(amt);
                    totalDeposits = totalDeposits.add(amt);
                } else if (tx.getAccountType() == AccountType.FINE && tx.getTransactionType() == TransactionType.REPAYMENT) {
                    fine = fine.add(amt);
                    totalFinesPaid = totalFinesPaid.add(amt);
                } else if (tx.getAccountType() == AccountType.MONTHLY_CONTRIBUTION && tx.getTransactionType() == TransactionType.ADDITION) {
                    contrib = contrib.add(amt);
                    totalMonthlyContributions = totalMonthlyContributions.add(amt);
                } else if (tx.getAccountType() == AccountType.FINANCIAL_AID) {
                    aid = aid.add(amt);
                    totalFinancialAidReceived = totalFinancialAidReceived.add(amt);
                }
            }

            BigDecimal meetingTotal = lRep.add(slRep).add(dep).add(fine).add(contrib);
            totalPaidInPeriod = totalPaidInPeriod.add(meetingTotal);

            meetingPayments.add(MemberPersonalReportDto.MemberMeetingPaymentEntryDto.builder()
                    .meetingId(m.getId())
                    .meetingNumber(m.getMeetingNumber())
                    .meetingDate(m.getMeetingDate().toString())
                    .loanRepayment(lRep)
                    .specialLoanRepayment(slRep)
                    .specialLoanTypeName(slTypeName)
                    .depositAddition(dep)
                    .finePayment(fine)
                    .contributionAddition(contrib)
                    .financialAidPayment(aid)
                    .totalPaid(meetingTotal)
                    .build());
        }

        // Interest for the month
        Optional<InterestCalculation> optIc = interestCalculationRepository.findByMemberIdAndInterestPeriod(memberId, finalYearMonth);
        BigDecimal monthInterest = optIc.map(InterestCalculation::getInterestAmount).orElse(BigDecimal.ZERO);

        // Fetch non-reversed LOAN transactions in chronological order
        List<FinancialTransaction> allLoanTxs = financialTransactionRepository
                .findByMemberIdAndAccountTypeOrderByCreatedAtAscIdAsc(memberId, AccountType.LOAN)
                .stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsReversed()))
                .toList();

        // If interest was recorded as transaction but not in repository
        if (monthInterest.compareTo(BigDecimal.ZERO) == 0) {
            monthInterest = allLoanTxs.stream()
                    .filter(t -> finalYearMonth.equals(getTransactionPeriod(t)) && t.getTransactionType() == TransactionType.INTEREST_APPLIED)
                    .map(FinancialTransaction::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        List<FinancialTransaction> loanTxsBefore = allLoanTxs.stream()
                .filter(t -> getTransactionPeriod(t).compareTo(finalYearMonth) < 0)
                .toList();

        List<FinancialTransaction> loanTxsInPeriod = allLoanTxs.stream()
                .filter(t -> getTransactionPeriod(t).equals(finalYearMonth))
                .toList();

        List<FinancialTransaction> loanTxsAfter = allLoanTxs.stream()
                .filter(t -> getTransactionPeriod(t).compareTo(finalYearMonth) > 0)
                .toList();

        BigDecimal startMonthRemainingLoanBalance;
        BigDecimal monthEndRemainingLoanBalance;

        if (optIc.isPresent() && optIc.get().getLoanBalanceUsed() != null && optIc.get().getLoanBalanceUsed().compareTo(BigDecimal.ZERO) > 0) {
            startMonthRemainingLoanBalance = optIc.get().getLoanBalanceUsed();
        } else if (!loanTxsInPeriod.isEmpty()) {
            FinancialTransaction firstTx = loanTxsInPeriod.get(0);
            if ((firstTx.getBalanceBefore() == null || firstTx.getBalanceBefore().compareTo(BigDecimal.ZERO) == 0)
                    && (firstTx.getTransactionType() == TransactionType.LOAN_ISSUED || firstTx.getTransactionType() == TransactionType.INITIAL_BALANCE)) {
                startMonthRemainingLoanBalance = firstTx.getBalanceAfter();
            } else {
                startMonthRemainingLoanBalance = firstTx.getBalanceBefore();
            }
        } else if (!loanTxsBefore.isEmpty()) {
            startMonthRemainingLoanBalance = loanTxsBefore.get(loanTxsBefore.size() - 1).getBalanceAfter();
        } else if (!loanTxsAfter.isEmpty()) {
            startMonthRemainingLoanBalance = loanTxsAfter.get(0).getBalanceBefore();
        } else {
            startMonthRemainingLoanBalance = balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO);
        }

        if (!loanTxsInPeriod.isEmpty()) {
            monthEndRemainingLoanBalance = loanTxsInPeriod.get(loanTxsInPeriod.size() - 1).getBalanceAfter();
        } else if (!loanTxsBefore.isEmpty()) {
            monthEndRemainingLoanBalance = loanTxsBefore.get(loanTxsBefore.size() - 1).getBalanceAfter();
        } else if (!loanTxsAfter.isEmpty()) {
            monthEndRemainingLoanBalance = loanTxsAfter.get(0).getBalanceBefore();
        } else {
            monthEndRemainingLoanBalance = startMonthRemainingLoanBalance;
        }

        if (startMonthRemainingLoanBalance == null) {
            startMonthRemainingLoanBalance = BigDecimal.ZERO;
        }
        if (monthEndRemainingLoanBalance == null) {
            monthEndRemainingLoanBalance = BigDecimal.ZERO;
        }

        // Fetch non-reversed DEPOSIT transactions in chronological order
        List<FinancialTransaction> allDepositTxs = financialTransactionRepository
                .findByMemberIdAndAccountTypeOrderByCreatedAtAscIdAsc(memberId, AccountType.DEPOSIT)
                .stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsReversed()))
                .toList();

        List<FinancialTransaction> depositTxsBefore = allDepositTxs.stream()
                .filter(t -> getTransactionPeriod(t).compareTo(finalYearMonth) < 0)
                .toList();

        List<FinancialTransaction> depositTxsInPeriod = allDepositTxs.stream()
                .filter(t -> getTransactionPeriod(t).equals(finalYearMonth))
                .toList();

        List<FinancialTransaction> depositTxsAfter = allDepositTxs.stream()
                .filter(t -> getTransactionPeriod(t).compareTo(finalYearMonth) > 0)
                .toList();

        BigDecimal monthEndDepositBalance;
        if (!depositTxsInPeriod.isEmpty()) {
            monthEndDepositBalance = depositTxsInPeriod.get(depositTxsInPeriod.size() - 1).getBalanceAfter();
        } else if (!depositTxsBefore.isEmpty()) {
            monthEndDepositBalance = depositTxsBefore.get(depositTxsBefore.size() - 1).getBalanceAfter();
        } else if (!depositTxsAfter.isEmpty()) {
            monthEndDepositBalance = depositTxsAfter.get(0).getBalanceBefore();
        } else {
            monthEndDepositBalance = balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO);
        }

        if (monthEndDepositBalance == null) {
            monthEndDepositBalance = BigDecimal.ZERO;
        }

        return MemberPersonalReportDto.builder()
                .memberId(member.getId())
                .memberNumber(member.getMemberNumber())
                .fullName(member.getFullName())
                .yearMonth(finalYearMonth)
                .availableMonths(availableMonths)
                .monthInterest(monthInterest)
                .startMonthRemainingLoanBalance(startMonthRemainingLoanBalance)
                .monthEndRemainingLoanBalance(monthEndRemainingLoanBalance)
                .totalDeposits(totalDeposits)
                .totalLoanRepaid(totalLoanRepaid)
                .totalSpecialLoanRepaid(totalSpecialLoanRepaid)
                .currentLoanBalance(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                .currentDepositBalance(balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO))
                .monthEndDepositBalance(monthEndDepositBalance)
                .totalMonthlyContributions(totalMonthlyContributions)
                .totalFinesPaid(totalFinesPaid)
                .totalFinancialAidReceived(totalFinancialAidReceived)
                .totalPaidInPeriod(totalPaidInPeriod)
                .meetingPayments(meetingPayments)
                .build();
    }

    private String getTransactionPeriod(FinancialTransaction tx) {
        if (tx.getMeeting() != null) {
            if (tx.getMeeting().getInterestPeriod() != null) {
                return tx.getMeeting().getInterestPeriod();
            }
            if (tx.getMeeting().getMeetingDate() != null) {
                return tx.getMeeting().getMeetingDate().toString().substring(0, 7);
            }
        }
        if (tx.getCreatedAt() != null) {
            return tx.getCreatedAt().toLocalDate().toString().substring(0, 7);
        }
        return LocalDate.now().toString().substring(0, 7);
    }

    @Transactional(readOnly = true)
    public CategoryReportDto getCategoryReport() {
        List<MemberAccount> accounts = memberAccountRepository.findAll();
        List<FinancialTransaction> transactions = financialTransactionRepository.findAll();

        BigDecimal totalLoansIssued = BigDecimal.ZERO;
        BigDecimal totalLoansRepaid = BigDecimal.ZERO;
        BigDecimal totalOutstandingLoanBalance = BigDecimal.ZERO;
        BigDecimal totalSpecialLoanBalance = BigDecimal.ZERO;
        BigDecimal totalDepositsCollected = BigDecimal.ZERO;
        BigDecimal totalFinancialAidDisbursed = BigDecimal.ZERO;
        BigDecimal totalContributionsCollected = BigDecimal.ZERO;
        BigDecimal totalFinesCollected = BigDecimal.ZERO;

        List<CategoryReportDto.CategoryMemberItemDto> loanMembers = new ArrayList<>();
        List<CategoryReportDto.CategoryMemberItemDto> depositMembers = new ArrayList<>();
        List<CategoryReportDto.FinancialAidHistoryItemDto> financialAidDisbursements = new ArrayList<>();

        for (MemberAccount acc : accounts) {
            BigDecimal bal = acc.getCurrentBalance() != null ? acc.getCurrentBalance() : BigDecimal.ZERO;
            Member m = acc.getMember();
            if (m == null || !Boolean.TRUE.equals(m.getIsActive())) continue;

            if (acc.getAccountType() == AccountType.LOAN && bal.compareTo(BigDecimal.ZERO) > 0) {
                totalOutstandingLoanBalance = totalOutstandingLoanBalance.add(bal);
                loanMembers.add(CategoryReportDto.CategoryMemberItemDto.builder()
                        .memberId(m.getId())
                        .memberNumber(m.getMemberNumber())
                        .fullName(m.getFullName())
                        .categoryName("സാധാരണ വായ്പ (Regular Loan)")
                        .balance(bal)
                        .build());
            } else if (acc.getAccountType() == AccountType.SPECIAL_LOAN && bal.compareTo(BigDecimal.ZERO) > 0) {
                totalSpecialLoanBalance = totalSpecialLoanBalance.add(bal);
                String splName = acc.getSpecialLoanType() != null ? acc.getSpecialLoanType().getName() : "സ്പെഷ്യൽ വായ്പ";
                loanMembers.add(CategoryReportDto.CategoryMemberItemDto.builder()
                        .memberId(m.getId())
                        .memberNumber(m.getMemberNumber())
                        .fullName(m.getFullName())
                        .categoryName(splName)
                        .balance(bal)
                        .build());
            } else if (acc.getAccountType() == AccountType.DEPOSIT && bal.compareTo(BigDecimal.ZERO) > 0) {
                totalDepositsCollected = totalDepositsCollected.add(bal);
                depositMembers.add(CategoryReportDto.CategoryMemberItemDto.builder()
                        .memberId(m.getId())
                        .memberNumber(m.getMemberNumber())
                        .fullName(m.getFullName())
                        .categoryName("നിക്ഷേപം (Deposit)")
                        .balance(bal)
                        .build());
            } else if (acc.getAccountType() == AccountType.MONTHLY_CONTRIBUTION) {
                totalContributionsCollected = totalContributionsCollected.add(bal);
            } else if (acc.getAccountType() == AccountType.FINE) {
                totalFinesCollected = totalFinesCollected.add(bal);
            }
        }

        for (FinancialTransaction tx : transactions) {
            if (Boolean.TRUE.equals(tx.getIsReversed())) continue;
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;

            if (tx.getAccountType() == AccountType.LOAN) {
                if (tx.getTransactionType() == TransactionType.LOAN_ISSUED) {
                    totalLoansIssued = totalLoansIssued.add(amt);
                } else if (tx.getTransactionType() == TransactionType.REPAYMENT) {
                    totalLoansRepaid = totalLoansRepaid.add(amt);
                }
            } else if (tx.getAccountType() == AccountType.FINANCIAL_AID && tx.getTransactionType() == TransactionType.ADDITION) {
                totalFinancialAidDisbursed = totalFinancialAidDisbursed.add(amt);
                Member m = tx.getMember();
                String mNum = m != null ? m.getMemberNumber() : "";
                String mName = m != null ? m.getFullName() : "Unknown Member";
                String mtNum = tx.getMeeting() != null ? String.valueOf(tx.getMeeting().getMeetingNumber()) : "-";
                Long mtId = tx.getMeeting() != null ? tx.getMeeting().getId() : null;

                financialAidDisbursements.add(CategoryReportDto.FinancialAidHistoryItemDto.builder()
                        .transactionId(tx.getId())
                        .memberId(m != null ? m.getId() : null)
                        .memberNumber(mNum)
                        .fullName(mName)
                        .amount(amt)
                        .transactionDate(tx.getCreatedAt() != null ? tx.getCreatedAt().toLocalDate().toString() : "")
                        .meetingId(mtId)
                        .meetingNumber(mtNum)
                        .notes(tx.getDescription())
                        .build());
            }
        }

        loanMembers.sort(Comparator.comparing(CategoryReportDto.CategoryMemberItemDto::getMemberNumber));
        depositMembers.sort(Comparator.comparing(CategoryReportDto.CategoryMemberItemDto::getMemberNumber));
        financialAidDisbursements.sort((a, b) -> b.getTransactionId().compareTo(a.getTransactionId()));

        return CategoryReportDto.builder()
                .totalLoansIssued(totalLoansIssued)
                .totalLoansRepaid(totalLoansRepaid)
                .totalOutstandingLoanBalance(totalOutstandingLoanBalance)
                .totalSpecialLoanBalance(totalSpecialLoanBalance)
                .loanMembers(loanMembers)
                .totalDepositsCollected(totalDepositsCollected)
                .depositMembers(depositMembers)
                .totalFinancialAidDisbursed(totalFinancialAidDisbursed)
                .financialAidDisbursements(financialAidDisbursements)
                .totalContributionsCollected(totalContributionsCollected)
                .totalFinesCollected(totalFinesCollected)
                .build();
    }
}
