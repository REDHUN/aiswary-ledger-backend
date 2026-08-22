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
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberProcessingService {

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final MemberRepository memberRepository;
    private final MemberAccountRepository memberAccountRepository;
    private final InterestCalculationRepository interestRepository;
    private final LedgerService ledgerService;

    @Transactional(readOnly = true)
    public MemberProcessingFormDto getMemberProcessingForm(Long meetingId, Long memberId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        MeetingMember meetingMember = meetingMemberRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("MeetingMember", "memberId", memberId));

        Map<AccountType, BigDecimal> balances = memberAccountRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(MemberAccount::getAccountType, MemberAccount::getCurrentBalance));

        boolean interestCalculated = interestRepository.existsByMemberIdAndInterestPeriod(memberId, meeting.getInterestPeriod());
        boolean interestCalculationRequired = meeting.getIsFirstMeetingOfMonth() || !interestCalculated;

        // Processing is allowed if interest is not required OR interest has been calculated
        boolean processingAllowed = !interestCalculationRequired || interestCalculated;

        return MemberProcessingFormDto.builder()
                .memberId(member.getId())
                .memberNumber(member.getMemberNumber())
                .fullName(member.getFullName())
                .loanRemaining(balances.getOrDefault(AccountType.LOAN, BigDecimal.ZERO))
                .interestRemaining(balances.getOrDefault(AccountType.INTEREST, BigDecimal.ZERO))
                .depositCurrent(balances.getOrDefault(AccountType.DEPOSIT, BigDecimal.ZERO))
                .fineRemaining(balances.getOrDefault(AccountType.FINE, BigDecimal.ZERO))
                .financialAidRemaining(balances.getOrDefault(AccountType.FINANCIAL_AID, BigDecimal.ZERO))
                .monthlyContributionCurrent(balances.getOrDefault(AccountType.MONTHLY_CONTRIBUTION, BigDecimal.ZERO))
                .processingStatus(meetingMember.getProcessingStatus())
                .interestCalculationRequired(interestCalculationRequired)
                .interestCalculated(interestCalculated)
                .processingAllowed(processingAllowed)
                .activeInterestPeriod(meeting.getInterestPeriod())
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

        // Critical Rule: Check interest status for first meeting of month or pending interest
        boolean interestCalculated = interestRepository.existsByMemberIdAndInterestPeriod(memberId, meeting.getInterestPeriod());
        boolean interestCalculationRequired = meeting.getIsFirstMeetingOfMonth() || !interestCalculated;

        if (interestCalculationRequired && !interestCalculated) {
            throw new InterestCalculationRequiredException(meeting.getInterestPeriod());
        }

        // Atomically record any non-zero financial transactions
        if (request.getLoanRepayment() != null && request.getLoanRepayment().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.LOAN, TransactionType.REPAYMENT, request.getLoanRepayment(), meetingId, "MEETING_REPAYMENT", null, request.getNotes(), null, operator);
        }

        if (request.getDepositAddition() != null && request.getDepositAddition().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.DEPOSIT, TransactionType.ADDITION, request.getDepositAddition(), meetingId, "MEETING_DEPOSIT", null, request.getNotes(), null, operator);
        }

        if (request.getFinePayment() != null && request.getFinePayment().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.FINE, TransactionType.ADDITION, request.getFinePayment(), meetingId, "MEETING_FINE", null, request.getNotes(), null, operator);
        }

        if (request.getFinancialAidPayment() != null && request.getFinancialAidPayment().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.FINANCIAL_AID, TransactionType.ADDITION, request.getFinancialAidPayment(), meetingId, "MEETING_FINANCIAL_AID", null, request.getNotes(), null, operator);
        }

        if (request.getMonthlyContributionAddition() != null && request.getMonthlyContributionAddition().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(memberId, AccountType.MONTHLY_CONTRIBUTION, TransactionType.ADDITION, request.getMonthlyContributionAddition(), meetingId, "MEETING_CONTRIBUTION", null, request.getNotes(), null, operator);
        }

        // Mark member as COMPLETED
        meetingMember.setProcessingStatus(MemberProcessingStatus.COMPLETED);
        meetingMember.setProcessedAt(ZonedDateTime.now());
        meetingMember.setProcessedBy(operator);
        meetingMemberRepository.save(meetingMember);

        return getMemberProcessingForm(meetingId, memberId);
    }
}
