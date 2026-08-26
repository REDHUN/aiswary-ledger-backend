package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.dto.request.ProcessMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.response.MemberProcessingFormDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.InterestCalculationRequiredException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberProcessingService {

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final MemberRepository memberRepository;
    private final MemberAccountRepository memberAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final InterestCalculationRepository interestRepository;
    private final InterestService interestService;
    private final LedgerService ledgerService;
    private final SpecialLoanTypeRepository specialLoanTypeRepository;

    @Transactional(readOnly = true)
    public MemberProcessingFormDto getMemberProcessingForm(Long meetingId, Long memberId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        MeetingMember meetingMember = meetingMemberRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("MeetingMember", "memberId", memberId));

        List<MemberAccount> accounts = memberAccountRepository.findByMemberId(memberId);
        Map<AccountType, BigDecimal> balances = accounts.stream()
                .filter(a -> a.getSpecialLoanType() == null)
                .collect(Collectors.toMap(MemberAccount::getAccountType, MemberAccount::getCurrentBalance, (a, b) -> a));

        Map<Long, BigDecimal> specialLoanBalances = accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.SPECIAL_LOAN && a.getSpecialLoanType() != null)
                .collect(Collectors.toMap(a -> a.getSpecialLoanType().getId(), MemberAccount::getCurrentBalance, (a, b) -> a));

        boolean interestCalculationRequired = meetingMember.getProcessingStatus() != MemberProcessingStatus.COMPLETED && interestService.isInterestCalculationRequired(memberId, meeting.getInterestPeriod());
        boolean interestCalculated = interestRepository.findByMemberIdAndInterestPeriod(memberId, meeting.getInterestPeriod()).isPresent();
        boolean processingAllowed = !interestCalculationRequired;

        BigDecimal calculatedInterestAmount = BigDecimal.ZERO;
        if (interestCalculated) {
            calculatedInterestAmount = interestRepository.findByMemberIdAndInterestPeriod(memberId, meeting.getInterestPeriod())
                    .map(InterestCalculation::getInterestAmount)
                    .orElse(BigDecimal.ZERO);
        }

        BigDecimal lastLoanRepayment = null;
        BigDecimal lastDepositAddition = null;
        BigDecimal lastFinePayment = null;
        BigDecimal lastFinancialAidPayment = null;
        BigDecimal lastMonthlyContributionAddition = null;
        String lastNotes = null;
        Map<Long, BigDecimal> lastSpecialLoanRepayments = new HashMap<>();

        if (meetingMember.getProcessingStatus() == MemberProcessingStatus.COMPLETED) {
            List<FinancialTransaction> txList = financialTransactionRepository.findByMeetingId(meetingId).stream()
                    .filter(tx -> tx.getMember().getId().equals(memberId)
                            && !Boolean.TRUE.equals(tx.getIsReversed())
                            && tx.getAccountType() != AccountType.INTEREST
                            && tx.getTransactionType() != TransactionType.REVERSAL
                            && tx.getTransactionType() != TransactionType.INTEREST_APPLIED
                            && !"INTEREST_CALCULATION".equals(tx.getReferenceType()))
                    .toList();

            for (FinancialTransaction tx : txList) {
                if (lastNotes == null && tx.getDescription() != null) {
                    lastNotes = tx.getDescription();
                }
                if (tx.getAccountType() == AccountType.LOAN && tx.getTransactionType() == TransactionType.REPAYMENT) {
                    lastLoanRepayment = tx.getAmount();
                } else if (tx.getAccountType() == AccountType.DEPOSIT && (tx.getTransactionType() == TransactionType.ADDITION || tx.getTransactionType() == TransactionType.INITIAL_BALANCE)) {
                    lastDepositAddition = tx.getAmount();
                } else if (tx.getAccountType() == AccountType.FINE && tx.getTransactionType() == TransactionType.REPAYMENT) {
                    lastFinePayment = tx.getAmount();
                } else if (tx.getAccountType() == AccountType.FINANCIAL_AID && tx.getTransactionType() == TransactionType.ADDITION) {
                    lastFinancialAidPayment = tx.getAmount();
                } else if (tx.getAccountType() == AccountType.MONTHLY_CONTRIBUTION && tx.getTransactionType() == TransactionType.ADDITION) {
                    lastMonthlyContributionAddition = tx.getAmount();
                } else if (tx.getAccountType() == AccountType.SPECIAL_LOAN && tx.getTransactionType() == TransactionType.REPAYMENT && tx.getSpecialLoanType() != null) {
                    lastSpecialLoanRepayments.put(tx.getSpecialLoanType().getId(), tx.getAmount());
                }
            }
        }

        List<SpecialLoanType> activeSpecialTypes = specialLoanTypeRepository.findByIsActiveTrue();
        List<MemberProcessingFormDto.MemberSpecialLoanBalanceDto> specialLoanDtos = new ArrayList<>();
        for (SpecialLoanType st : activeSpecialTypes) {
            BigDecimal bal = specialLoanBalances.getOrDefault(st.getId(), BigDecimal.ZERO);
            BigDecimal lastAmt = lastSpecialLoanRepayments.get(st.getId());
            if (bal.compareTo(BigDecimal.ZERO) > 0 || lastAmt != null) {
                specialLoanDtos.add(MemberProcessingFormDto.MemberSpecialLoanBalanceDto.builder()
                        .specialLoanTypeId(st.getId())
                        .specialLoanTypeName(st.getName())
                        .currentBalance(bal)
                        .lastRepaymentAmount(lastAmt)
                        .build());
            }
        }

        return MemberProcessingFormDto.builder()
                .memberId(member.getId())
                .memberNumber(member.getMemberNumber())
                .fullName(member.getFullName())
                .loanRemaining(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                .interestRemaining(calculatedInterestAmount)
                .calculatedInterestAmount(calculatedInterestAmount)
                .depositCurrent(balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO))
                .fineRemaining(balances.getOrDefault(AccountType.FINE, BigDecimal.ZERO))
                .financialAidRemaining(balances.getOrDefault(AccountType.FINANCIAL_AID, BigDecimal.ZERO))
                .monthlyContributionCurrent(balances.getOrDefault(AccountType.MONTHLY_CONTRIBUTION, BigDecimal.ZERO))
                .processingStatus(meetingMember.getProcessingStatus())
                .interestCalculationRequired(interestCalculationRequired)
                .interestCalculated(interestCalculated)
                .processingAllowed(processingAllowed)
                .activeInterestPeriod(meeting.getInterestPeriod())
                .lastLoanRepayment(lastLoanRepayment)
                .lastDepositAddition(lastDepositAddition)
                .lastFinePayment(lastFinePayment)
                .lastFinancialAidPayment(lastFinancialAidPayment)
                .lastMonthlyContributionAddition(lastMonthlyContributionAddition)
                .lastNotes(lastNotes)
                .specialLoans(specialLoanDtos)
                .build();
    }

    @Transactional
    public MemberProcessingFormDto processMember(Long meetingId, Long memberId, ProcessMemberRequest request, User operator) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        MeetingMember meetingMember = meetingMemberRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("MeetingMember", "memberId", memberId));

        if (meetingMember.getProcessingStatus() == MemberProcessingStatus.COMPLETED) {
            if (!Boolean.TRUE.equals(request.getIsUpdate())) {
                throw new BusinessException("MEMBER_ALREADY_PROCESSED", "Member payment has already been processed for this meeting. Please use the update option to modify.");
            }

            List<FinancialTransaction> existingTxList = financialTransactionRepository.findByMeetingId(meetingId).stream()
                    .filter(tx -> tx.getMember().getId().equals(memberId)
                            && !Boolean.TRUE.equals(tx.getIsReversed())
                            && tx.getAccountType() != AccountType.INTEREST
                            && tx.getTransactionType() != TransactionType.REVERSAL
                            && tx.getTransactionType() != TransactionType.INTEREST_APPLIED
                            && !"INTEREST_CALCULATION".equals(tx.getReferenceType()))
                    .toList();

            for (FinancialTransaction tx : existingTxList) {
                ledgerService.reverseTransaction(tx.getId(), "UPDATE_MEETING_PAYMENT", operator);
            }
        }

        boolean interestCalculationRequired = meetingMember.getProcessingStatus() != MemberProcessingStatus.COMPLETED && interestService.isInterestCalculationRequired(memberId, meeting.getInterestPeriod());

        if (interestCalculationRequired) {
            throw new InterestCalculationRequiredException(meeting.getInterestPeriod());
        }

        if (request.getLoanRepayment() != null && request.getLoanRepayment().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.LOAN, TransactionType.REPAYMENT, request.getLoanRepayment(), meetingId, "MEETING_REPAYMENT", null, request.getNotes(), null, request.getTransactionDate(), operator);
        }

        if (request.getDepositAddition() != null && request.getDepositAddition().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.DEPOSIT, TransactionType.ADDITION, request.getDepositAddition(), meetingId, "MEETING_DEPOSIT", null, request.getNotes(), null, request.getTransactionDate(), operator);
        }

        if (request.getFinePayment() != null && request.getFinePayment().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.FINE, TransactionType.REPAYMENT, request.getFinePayment(), meetingId, "MEETING_FINE_PAYMENT", null, request.getNotes(), null, request.getTransactionDate(), operator);
        }

        if (request.getFinancialAidPayment() != null && request.getFinancialAidPayment().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.FINANCIAL_AID, TransactionType.ADDITION, request.getFinancialAidPayment(), meetingId, "MEETING_FINANCIAL_AID_GIVEN", null, request.getNotes(), null, request.getTransactionDate(), operator);
        }

        if (request.getMonthlyContributionAddition() != null && request.getMonthlyContributionAddition().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.MONTHLY_CONTRIBUTION, TransactionType.ADDITION, request.getMonthlyContributionAddition(), meetingId, "MEETING_CONTRIBUTION", null, request.getNotes(), null, request.getTransactionDate(), operator);
        }

        if (request.getSpecialLoanRepayments() != null) {
            for (ProcessMemberRequest.SpecialLoanRepaymentRequest slReq : request.getSpecialLoanRepayments()) {
                if (slReq.getAmount() != null && slReq.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    SpecialLoanType slType = specialLoanTypeRepository.findById(slReq.getSpecialLoanTypeId())
                            .orElseThrow(() -> new ResourceNotFoundException("SpecialLoanType", "id", slReq.getSpecialLoanTypeId()));
                    ledgerService.recordTransaction(memberId, AccountType.SPECIAL_LOAN, TransactionType.REPAYMENT, slReq.getAmount(), meetingId, "MEETING_SPECIAL_LOAN_REPAYMENT", null, request.getNotes(), null, request.getTransactionDate(), slType, operator);
                }
            }
        }

        meetingMember.setProcessingStatus(MemberProcessingStatus.COMPLETED);
        meetingMember.setProcessedAt(ZonedDateTime.now());
        meetingMember.setProcessedBy(operator);
        meetingMemberRepository.save(meetingMember);

        return getMemberProcessingForm(meetingId, memberId);
    }
}
