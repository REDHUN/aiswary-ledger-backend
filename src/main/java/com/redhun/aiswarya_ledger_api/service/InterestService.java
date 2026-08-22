package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.InterestStatus;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.dto.response.InterestCalculationDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.DuplicateResourceException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.InterestCalculationRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberAccountRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterestService {

    private final InterestCalculationRepository interestRepository;
    private final MemberRepository memberRepository;
    private final MemberAccountRepository memberAccountRepository;
    private final MeetingRepository meetingRepository;
    private final LedgerService ledgerService;

    public static final BigDecimal INTEREST_RATE = new BigDecimal("0.0100"); // 1%

    /**
     * Checks whether a member requires an interest calculation for the given period.
     */
    @Transactional(readOnly = true)
    public boolean isInterestCalculationRequired(Long memberId, String interestPeriod) {
        return !interestRepository.existsByMemberIdAndInterestPeriod(memberId, interestPeriod);
    }

    /**
     * Calculates 1% monthly interest for a specific member for an interest period.
     */
    @Transactional
    public InterestCalculationDto calculateInterest(Long memberId, String interestPeriod, Long meetingId, User operator) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        // Enforce 1 interest calculation per member per month constraint
        if (interestRepository.existsByMemberIdAndInterestPeriod(memberId, interestPeriod)) {
            throw new BusinessException("DUPLICATE_INTEREST_CALCULATION",
                    String.format("Interest for period '%s' has already been calculated for member %s", interestPeriod, member.getMemberNumber()));
        }

        // Fetch current outstanding LOAN balance
        MemberAccount loanAccount = memberAccountRepository.findByMemberIdAndAccountType(memberId, AccountType.LOAN)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Loan account not found for member"));

        BigDecimal loanBalanceUsed = loanAccount.getCurrentBalance();
        // Truncate decimal paise (e.g. ₹4.99 becomes ₹4.00) using RoundingMode.DOWN
        BigDecimal interestAmount = loanBalanceUsed.multiply(INTEREST_RATE).setScale(0, RoundingMode.DOWN).setScale(2);

        InterestCalculation calculation = InterestCalculation.builder()
                .member(member)
                .interestPeriod(interestPeriod)
                .meeting(meeting)
                .loanBalanceUsed(loanBalanceUsed)
                .interestRate(INTEREST_RATE)
                .interestAmount(interestAmount)
                .status(InterestStatus.CALCULATED)
                .calculatedBy(operator)
                .build();

        calculation = interestRepository.save(calculation);

        // If interest amount > 0, post INTEREST_APPLIED ledger transaction directly to member's LOAN account balance
        if (interestAmount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordTransaction(
                    memberId,
                    AccountType.LOAN,
                    TransactionType.INTEREST_APPLIED,
                    interestAmount,
                    meetingId,
                    "INTEREST_CALCULATION",
                    calculation.getId(),
                    "Monthly interest (1%) capitalized to loan for period " + interestPeriod,
                    null,
                    operator
            );
        }

        return mapToDto(calculation);
    }

    @Transactional(readOnly = true)
    public List<InterestCalculationDto> getMemberInterestHistory(Long memberId) {
        return interestRepository.findByMemberIdOrderByCalculatedAtDesc(memberId).stream()
                .map(this::mapToDto)
                .toList();
    }

    public InterestCalculationDto mapToDto(InterestCalculation ic) {
        return InterestCalculationDto.builder()
                .id(ic.getId())
                .memberId(ic.getMember().getId())
                .memberName(ic.getMember().getFullName())
                .interestPeriod(ic.getInterestPeriod())
                .meetingId(ic.getMeeting().getId())
                .loanBalanceUsed(ic.getLoanBalanceUsed())
                .interestRate(ic.getInterestRate())
                .interestAmount(ic.getInterestAmount())
                .status(ic.getStatus())
                .calculatedAt(ic.getCalculatedAt())
                .build();
    }
}
