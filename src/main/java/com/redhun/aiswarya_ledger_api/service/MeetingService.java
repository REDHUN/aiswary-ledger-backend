package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.dto.request.RescheduleMeetingRequest;
import com.redhun.aiswarya_ledger_api.dto.request.ScheduleMeetingRequest;
import com.redhun.aiswarya_ledger_api.dto.response.CompletedMeetingRegisterDto;
import com.redhun.aiswarya_ledger_api.dto.response.MeetingDto;
import com.redhun.aiswarya_ledger_api.dto.response.MeetingMemberDto;
import com.redhun.aiswarya_ledger_api.dto.response.SpecialLoanRegisterItemDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.MembersPendingException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.FinancialTransactionRepository;
import com.redhun.aiswarya_ledger_api.repository.GroupExpenseRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingMemberRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final MemberRepository memberRepository;
    private final SystemSettingService systemSettingService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final GroupExpenseRepository groupExpenseRepository;

    @Transactional
    public MeetingDto scheduleMeeting(ScheduleMeetingRequest request, User operator) {
        // Enforce Single Active/Uncompleted Meeting Rule
        List<Meeting> uncompleted = meetingRepository.findByStatusIn(List.of(MeetingStatus.SCHEDULED, MeetingStatus.OPEN));
        if (!uncompleted.isEmpty()) {
            Meeting existing = uncompleted.get(0);
            throw new BusinessException(
                    "UNCOMPLETED_MEETING_EXISTS",
                    "Cannot schedule a new meeting while Meeting #" + existing.getMeetingNumber() +
                    " (Date: " + existing.getMeetingDate() + ") is currently " + existing.getStatus() +
                    ". Please complete or cancel the existing meeting first."
            );
        }

        if (meetingRepository.existsByMeetingDate(request.getMeetingDate())) {
            throw new BusinessException("DUPLICATE_MEETING_DATE", "A meeting is already scheduled on date: " + request.getMeetingDate());
        }

        String interestPeriod = request.getMeetingDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int nextNumber = meetingRepository.findFirstByOrderByMeetingNumberDesc()
                .map(m -> m.getMeetingNumber() + 1)
                .orElse(1);

        Meeting meeting = Meeting.builder()
                .meetingNumber(nextNumber)
                .meetingDate(request.getMeetingDate())
                .status(MeetingStatus.SCHEDULED)
                .interestPeriod(interestPeriod)
                .isFirstMeetingOfMonth(false)
                .notes(request.getNotes())
                .createdBy(operator)
                .build();

        meeting = meetingRepository.save(meeting);

        // Recalculate first-meeting-of-month flags chronologically
        recalculateFirstMeetingFlags(interestPeriod);

        meeting = meetingRepository.findById(meeting.getId()).orElse(meeting);
        return mapToDto(meeting);
    }

    @Transactional
    public MeetingDto openMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new BusinessException("INVALID_MEETING_STATE", "Only SCHEDULED meetings can be opened");
        }

        meeting.setStatus(MeetingStatus.OPEN);
        meeting.setOpenedAt(ZonedDateTime.now());
        meeting = meetingRepository.save(meeting);

        // Snapshot all active members into meeting_members with PENDING status
        List<Member> activeMembers = memberRepository.findByIsActiveTrue();
        for (Member member : activeMembers) {
            if (meetingMemberRepository.findByMeetingIdAndMemberId(meeting.getId(), member.getId()).isEmpty()) {
                MeetingMember mm = MeetingMember.builder()
                        .meeting(meeting)
                        .member(member)
                        .processingStatus(MemberProcessingStatus.PENDING)
                        .build();
                meetingMemberRepository.save(mm);
            }
        }

        return mapToDto(meeting);
    }

    @Transactional
    public MeetingDto completeMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        if (meeting.getStatus() != MeetingStatus.OPEN) {
            throw new BusinessException("INVALID_MEETING_STATE", "Only OPEN meetings can be completed");
        }

        // Critical Rule: Reject completion if any assigned member is pending
        long pendingCount = meetingMemberRepository.countByMeetingIdAndProcessingStatus(meetingId, MemberProcessingStatus.PENDING);
        if (pendingCount > 0) {
            List<String> pendingMemberNames = meetingMemberRepository
                    .findByMeetingIdAndProcessingStatusWithMember(meetingId, MemberProcessingStatus.PENDING)
                    .stream()
                    .map(mm -> mm.getMember().getFullName())
                    .collect(Collectors.toList());

            throw new MembersPendingException(pendingCount, pendingMemberNames);
        }

        meeting.setStatus(MeetingStatus.COMPLETED);
        meeting.setCompletedAt(ZonedDateTime.now());
        meeting = meetingRepository.save(meeting);

        // Automatically update Surplus Amount (മിച്ച തുക = Current + Collections - Financial Aid)
        updateSurplusAmountOnMeetingCompletion(meetingId);

        // Automatically schedule next Sunday meeting
        scheduleNextSundayMeeting(meeting.getMeetingDate(), meeting.getCreatedBy());

        return mapToDto(meeting);
    }

    private void updateSurplusAmountOnMeetingCompletion(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
        if (meeting == null) return;

        BigDecimal collections = financialTransactionRepository.sumMeetingCollectionsExcludingAid(
                meetingId, TransactionType.REVERSAL, AccountType.FINANCIAL_AID, TransactionType.INTEREST_APPLIED);
        if (collections == null) collections = BigDecimal.ZERO;

        BigDecimal aid = financialTransactionRepository.sumMeetingFinancialAid(
                meetingId, TransactionType.REVERSAL, AccountType.FINANCIAL_AID);
        if (aid == null) aid = BigDecimal.ZERO;

        BigDecimal netChange = collections.subtract(aid);

        BigDecimal currentSurplus = systemSettingService.getSurplusAmount();
        BigDecimal newSurplus = currentSurplus.add(netChange);
        if (newSurplus.compareTo(BigDecimal.ZERO) < 0) {
            newSurplus = BigDecimal.ZERO;
        }

        systemSettingService.updateSurplusAmount(newSurplus);
        meeting.setSurplusAmount(newSurplus);
        meeting = meetingRepository.save(meeting);

        if (netChange.compareTo(BigDecimal.ZERO) > 0) {
            User operator = meeting.getCreatedBy();
            Member member = memberRepository.findByUserId(operator != null ? operator.getId() : null)
                    .orElseGet(() -> memberRepository.findByIsActiveTrue().stream().findFirst().orElse(null));

            if (member != null) {
                FinancialTransaction tx = FinancialTransaction.builder()
                        .member(member)
                        .accountType(AccountType.DEPOSIT)
                        .transactionType(TransactionType.ADDITION)
                        .amount(netChange)
                        .balanceBefore(currentSurplus)
                        .balanceAfter(newSurplus)
                        .meeting(meeting)
                        .referenceType("MEETING_SURPLUS_TRANSFER")
                        .description("Meeting #" + meeting.getMeetingNumber() + " Net Collections added to Surplus Fund (മിച്ച തുക)")
                        .createdBy(operator)
                        .createdAt(ZonedDateTime.now())
                        .isReversed(false)
                        .build();

                financialTransactionRepository.save(tx);
            }
        }
    }

    @Transactional
    public MeetingDto rescheduleMeeting(Long meetingId, RescheduleMeetingRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));

        if (meeting.getStatus() == MeetingStatus.COMPLETED || meeting.getStatus() == MeetingStatus.CANCELLED) {
            throw new BusinessException("INVALID_MEETING_STATE", "Cannot reschedule a COMPLETED or CANCELLED meeting");
        }

        String oldPeriod = meeting.getInterestPeriod();
        meeting.setMeetingDate(request.getNewMeetingDate());
        String newPeriod = request.getNewMeetingDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        meeting.setInterestPeriod(newPeriod);

        meeting = meetingRepository.save(meeting);

        recalculateFirstMeetingFlags(oldPeriod);
        if (!oldPeriod.equals(newPeriod)) {
            recalculateFirstMeetingFlags(newPeriod);
        }

        meeting = meetingRepository.findById(meeting.getId()).orElse(meeting);
        return mapToDto(meeting);
    }

    private void recalculateFirstMeetingFlags(String interestPeriod) {
        List<Meeting> sortedMeetings = meetingRepository.findMeetingsInPeriodSorted(interestPeriod);
        if (!sortedMeetings.isEmpty()) {
            Meeting earliest = sortedMeetings.get(0);
            for (Meeting m : sortedMeetings) {
                boolean shouldBeFirst = m.getId().equals(earliest.getId());
                if (!shouldBeFirst == m.getIsFirstMeetingOfMonth()) {
                    m.setIsFirstMeetingOfMonth(shouldBeFirst);
                    meetingRepository.save(m);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public CompletedMeetingRegisterDto getCompletedMeetingRegister(Long meetingId) {
        Meeting meeting;
        if (meetingId != null) {
            meeting = meetingRepository.findById(meetingId)
                    .orElse(null);
        } else {
            meeting = meetingRepository.findTop1ByStatusOrderByMeetingDateDescMeetingNumberDesc(MeetingStatus.COMPLETED)
                    .orElse(null);
        }

        if (meeting == null) {
            return null;
        }

        Long mId = meeting.getId();

        BigDecimal deposits = financialTransactionRepository.sumMeetingCategory(mId, AccountType.DEPOSIT, TransactionType.ADDITION);
        if (deposits == null) deposits = BigDecimal.ZERO;

        BigDecimal loanRepayments = financialTransactionRepository.sumMeetingCategory(mId, AccountType.LOAN, TransactionType.REPAYMENT);
        if (loanRepayments == null) loanRepayments = BigDecimal.ZERO;

        BigDecimal fines = financialTransactionRepository.sumMeetingCategory(mId, AccountType.FINE, TransactionType.REPAYMENT);
        if (fines == null) fines = BigDecimal.ZERO;

        BigDecimal contributions = financialTransactionRepository.sumMeetingCategory(mId, AccountType.MONTHLY_CONTRIBUTION, TransactionType.ADDITION);
        if (contributions == null) contributions = BigDecimal.ZERO;

        BigDecimal specialLoanRepayments = financialTransactionRepository.sumMeetingCategory(mId, AccountType.SPECIAL_LOAN, TransactionType.REPAYMENT);
        if (specialLoanRepayments == null) specialLoanRepayments = BigDecimal.ZERO;

        List<Object[]> slRows = financialTransactionRepository.sumMeetingSpecialLoansByType(mId);
        List<SpecialLoanRegisterItemDto> slBreakdown = slRows.stream().map(r -> SpecialLoanRegisterItemDto.builder()
                .specialLoanTypeId((Long) r[0])
                .specialLoanTypeName((String) r[1] != null ? (String) r[1] : "Special Loan")
                .amount((BigDecimal) r[2])
                .build()).collect(Collectors.toList());

        BigDecimal aidDisbursed = financialTransactionRepository.sumMeetingCategory(mId, AccountType.FINANCIAL_AID, TransactionType.ADDITION);
        if (aidDisbursed == null) aidDisbursed = BigDecimal.ZERO;

        BigDecimal groupExpenses = groupExpenseRepository.sumMeetingExpenses(mId);
        if (groupExpenses == null) groupExpenses = BigDecimal.ZERO;

        BigDecimal totalCollections = deposits.add(loanRepayments).add(fines).add(contributions).add(specialLoanRepayments);
        BigDecimal netCollections = totalCollections.subtract(aidDisbursed).subtract(groupExpenses);

        BigDecimal surplus = meeting.getSurplusAmount();
        if (surplus == null) {
            surplus = calculateCumulativeSurplusUpToMeeting(meeting);
        }

        return CompletedMeetingRegisterDto.builder()
                .meetingId(meeting.getId())
                .meetingNumber(meeting.getMeetingNumber())
                .meetingDate(meeting.getMeetingDate())
                .interestPeriod(meeting.getInterestPeriod())
                .totalDepositsCollected(deposits)
                .totalLoanRepaymentsCollected(loanRepayments)
                .totalFinesCollected(fines)
                .totalMonthlyContributionsCollected(contributions)
                .totalSpecialLoanRepaymentsCollected(specialLoanRepayments)
                .specialLoanBreakdown(slBreakdown)
                .totalFinancialAidDisbursed(aidDisbursed)
                .totalGroupExpenses(groupExpenses)
                .totalNetMeetingCollections(netCollections)
                .surplusAmount(surplus)
                .build();
    }

    private BigDecimal calculateCumulativeSurplusUpToMeeting(Meeting targetMeeting) {
        List<Meeting> priorCompleted = meetingRepository.findByStatusOrderByMeetingDateDescMeetingNumberDesc(MeetingStatus.COMPLETED)
                .stream()
                .filter(m -> m.getMeetingNumber() != null && targetMeeting.getMeetingNumber() != null && m.getMeetingNumber() <= targetMeeting.getMeetingNumber())
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        for (Meeting m : priorCompleted) {
            BigDecimal col = financialTransactionRepository.sumMeetingCollectionsExcludingAid(
                    m.getId(), TransactionType.REVERSAL, AccountType.FINANCIAL_AID, TransactionType.INTEREST_APPLIED);
            if (col == null) col = BigDecimal.ZERO;
            BigDecimal aid = financialTransactionRepository.sumMeetingFinancialAid(
                    m.getId(), TransactionType.REVERSAL, AccountType.FINANCIAL_AID);
            if (aid == null) aid = BigDecimal.ZERO;
            total = total.add(col.subtract(aid));
        }
        return total.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : total;
    }

    private void scheduleNextSundayMeeting(LocalDate currentMeetingDate, User operator) {
        LocalDate nextSunday = currentMeetingDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        if (!meetingRepository.existsByMeetingDate(nextSunday)) {
            ScheduleMeetingRequest req = new ScheduleMeetingRequest();
            req.setMeetingDate(nextSunday);
            req.setNotes("Automatically scheduled next Sunday meeting");
            scheduleMeeting(req, operator);
        }
    }

    @Transactional(readOnly = true)
    public List<MeetingDto> getAllMeetings() {
        return meetingRepository.findAllByOrderByMeetingDateDescMeetingNumberDesc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MeetingDto getMeetingById(Long id) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", id));
        return mapToDto(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingMemberDto> getMeetingMembers(Long meetingId) {
        return meetingMemberRepository.findByMeetingId(meetingId).stream()
                .map(mm -> MeetingMemberDto.builder()
                        .id(mm.getId())
                        .meetingId(mm.getMeeting().getId())
                        .memberId(mm.getMember().getId())
                        .memberNumber(mm.getMember().getMemberNumber())
                        .fullName(mm.getMember().getFullName())
                        .processingStatus(mm.getProcessingStatus())
                        .processedAt(mm.getProcessedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public MeetingDto mapToDto(Meeting meeting) {
        long totalMembers = meetingMemberRepository.countByMeetingId(meeting.getId());
        long processedMembers = meetingMemberRepository.countByMeetingIdAndProcessingStatus(meeting.getId(), MemberProcessingStatus.COMPLETED);
        long pendingMembers;

        if (totalMembers == 0 && meeting.getStatus() == MeetingStatus.SCHEDULED) {
            long activeCount = memberRepository.findByIsActiveTrue().size();
            totalMembers = activeCount;
            processedMembers = 0;
            pendingMembers = activeCount;
        } else {
            pendingMembers = totalMembers - processedMembers;
        }

        // Dynamically verify chronological first-meeting status
        List<Meeting> sortedPeriodMeetings = meetingRepository.findMeetingsInPeriodSorted(meeting.getInterestPeriod());
        boolean isFirstMeeting = !sortedPeriodMeetings.isEmpty() && sortedPeriodMeetings.get(0).getId().equals(meeting.getId());

        return MeetingDto.builder()
                .id(meeting.getId())
                .meetingNumber(meeting.getMeetingNumber())
                .meetingDate(meeting.getMeetingDate())
                .status(meeting.getStatus())
                .interestPeriod(meeting.getInterestPeriod())
                .isFirstMeetingOfMonth(isFirstMeeting)
                .notes(meeting.getNotes())
                .openedAt(meeting.getOpenedAt())
                .completedAt(meeting.getCompletedAt())
                .surplusAmount(meeting.getSurplusAmount())
                .totalMembers(totalMembers)
                .processedMembers(processedMembers)
                .pendingMembers(pendingMembers)
                .build();
    }
}
