package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import java.util.Optional;
import com.redhun.aiswarya_ledger_api.dto.request.IssueGroupLoanRequest;
import com.redhun.aiswarya_ledger_api.dto.response.GroupLoanSummaryDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.GroupLoanRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberGroupRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import com.redhun.aiswarya_ledger_api.repository.SpecialLoanTypeRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupLoanService {

    private final GroupLoanRepository groupLoanRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final MemberRepository memberRepository;
    private final SpecialLoanTypeRepository specialLoanTypeRepository;
    private final LedgerService ledgerService;
    private final SystemSettingService systemSettingService;
    private final com.redhun.aiswarya_ledger_api.repository.MemberAccountRepository memberAccountRepository;
    private final MeetingRepository meetingRepository;

    @Transactional
    public GroupLoanSummaryDto issueGroupLoan(IssueGroupLoanRequest request, User operator) {
        List<Long> memberIds = request.getMemberIds();
        if (memberIds == null || memberIds.isEmpty()) {
            throw new BusinessException("EMPTY_MEMBER_LIST", "At least one member must be selected to issue a group loan");
        }

        int memberCount = memberIds.size();
        BigDecimal totalAmount = request.getTotalAmount();
        BigDecimal perMemberAmount = totalAmount.divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP);

        MemberGroup group = null;
        if (request.getGroupId() != null) {
            group = memberGroupRepository.findById(request.getGroupId()).orElse(null);
        }

        AccountType accountType = AccountType.LOAN;
        SpecialLoanType specialLoanType = null;
        if (request.getSpecialLoanTypeId() != null) {
            accountType = AccountType.SPECIAL_LOAN;
            specialLoanType = specialLoanTypeRepository.findById(request.getSpecialLoanTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("SpecialLoanType", "id", request.getSpecialLoanTypeId()));
        }

        Meeting meeting = null;
        if (request.getMeetingId() != null) {
            meeting = meetingRepository.findById(request.getMeetingId()).orElse(null);
        } else {
            Optional<Meeting> openMeeting = meetingRepository.findFirstByStatusOrderByMeetingDateAsc(MeetingStatus.OPEN);
            if (openMeeting.isPresent()) {
                meeting = openMeeting.get();
            } else {
                Optional<Meeting> completedMeeting = meetingRepository.findTop1ByStatusOrderByMeetingDateDescMeetingNumberDesc(MeetingStatus.COMPLETED);
                if (completedMeeting.isPresent()) {
                    meeting = completedMeeting.get();
                }
            }
        }
        Long targetMeetingId = meeting != null ? meeting.getId() : null;

        LocalDate txDate = request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now();
        String groupNameStr = group != null ? group.getName() : "Group Loan (" + memberCount + " members)";
        String description = String.format("Group Loan [%s]: Total ₹%s split ₹%s each (%d members)%s",
                groupNameStr, totalAmount, perMemberAmount, memberCount,
                request.getNotes() != null && !request.getNotes().isEmpty() ? " - " + request.getNotes() : "");

        // Record individual loan transactions for each selected member
        for (Long memberId : memberIds) {
            if (!memberRepository.existsById(memberId)) {
                throw new ResourceNotFoundException("Member", "id", memberId);
            }

            ledgerService.recordTransaction(
                    memberId,
                    accountType,
                    TransactionType.LOAN_ISSUED,
                    perMemberAmount,
                    request.getMeetingId(),
                    "GROUP_LOAN_ISSUE",
                    null,
                    description,
                    null,
                    txDate,
                    specialLoanType,
                    operator
            );
        }

        // Record summary GroupLoan audit record
        GroupLoan groupLoan = GroupLoan.builder()
                .group(group)
                .accountType(accountType)
                .specialLoanType(specialLoanType)
                .totalAmount(totalAmount)
                .perMemberAmount(perMemberAmount)
                .memberCount(memberCount)
                .notes(request.getNotes())
                .transactionDate(txDate)
                .createdBy(operator)
                .build();

        groupLoan = groupLoanRepository.save(groupLoan);

        // Deduct total group loan amount directly from Surplus Fund (michha thukka)
        BigDecimal currentSurplus = systemSettingService.getSurplusAmount();
        BigDecimal newSurplus = currentSurplus.subtract(totalAmount);
        if (newSurplus.compareTo(BigDecimal.ZERO) < 0) {
            newSurplus = BigDecimal.ZERO;
        }
        systemSettingService.updateSurplusAmount(newSurplus);
        if (meeting != null) {
            BigDecimal mSurplus = meeting.getSurplusAmount() != null ? meeting.getSurplusAmount() : currentSurplus;
            meeting.setSurplusAmount(mSurplus.subtract(totalAmount).compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : mSurplus.subtract(totalAmount));
            meetingRepository.save(meeting);
        }

        return GroupLoanSummaryDto.builder()
                .id(groupLoan.getId())
                .groupId(group != null ? group.getId() : null)
                .groupName(group != null ? group.getName() : null)
                .accountType(accountType)
                .specialLoanTypeId(specialLoanType != null ? specialLoanType.getId() : null)
                .specialLoanTypeName(specialLoanType != null ? specialLoanType.getName() : null)
                .totalAmount(totalAmount)
                .perMemberAmount(perMemberAmount)
                .memberCount(memberCount)
                .notes(request.getNotes())
                .transactionDate(txDate)
                .memberIds(memberIds)
                .createdAt(groupLoan.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public GroupLoanSummaryDto getGroupLoanById(Long id) {
        GroupLoan gl = groupLoanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GroupLoan", "id", id));
        return mapToDto(gl);
    }

    @Transactional(readOnly = true)
    public List<GroupLoanSummaryDto> getAllGroupLoans() {
        return groupLoanRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public GroupLoanSummaryDto mapToDto(GroupLoan gl) {
        BigDecimal totalRepaid = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;
        java.util.List<com.redhun.aiswarya_ledger_api.dto.response.GroupLoanMemberDetailDto> memberDetails = new java.util.ArrayList<>();

        if (gl.getGroup() != null && gl.getGroup().getMembers() != null) {
            for (Member member : gl.getGroup().getMembers()) {
                MemberAccount account = memberAccountRepository
                        .findByMemberIdAndAccountTypeAndSpecialLoanType(
                                member.getId(),
                                gl.getAccountType(),
                                gl.getSpecialLoanType() != null ? gl.getSpecialLoanType().getId() : null
                        ).orElse(null);

                BigDecimal currentBal = account != null ? account.getCurrentBalance() : BigDecimal.ZERO;
                BigDecimal issuedAmt = gl.getPerMemberAmount();
                BigDecimal repaidAmt = issuedAmt.subtract(currentBal);
                if (repaidAmt.compareTo(BigDecimal.ZERO) < 0) {
                    repaidAmt = BigDecimal.ZERO;
                }

                totalRepaid = totalRepaid.add(repaidAmt);
                totalRemaining = totalRemaining.add(currentBal);

                memberDetails.add(com.redhun.aiswarya_ledger_api.dto.response.GroupLoanMemberDetailDto.builder()
                        .memberId(member.getId())
                        .memberNumber(member.getMemberNumber())
                        .fullName(member.getFullName())
                        .issuedAmount(issuedAmt)
                        .repaidAmount(repaidAmt)
                        .currentBalance(currentBal)
                        .isFullyRepaid(currentBal.compareTo(BigDecimal.ZERO) == 0)
                        .build());
            }
        }

        return GroupLoanSummaryDto.builder()
                .id(gl.getId())
                .groupId(gl.getGroup() != null ? gl.getGroup().getId() : null)
                .groupName(gl.getGroup() != null ? gl.getGroup().getName() : null)
                .accountType(gl.getAccountType())
                .specialLoanTypeId(gl.getSpecialLoanType() != null ? gl.getSpecialLoanType().getId() : null)
                .specialLoanTypeName(gl.getSpecialLoanType() != null ? gl.getSpecialLoanType().getName() : null)
                .totalAmount(gl.getTotalAmount())
                .perMemberAmount(gl.getPerMemberAmount())
                .memberCount(gl.getMemberCount())
                .totalRepaidAmount(totalRepaid)
                .totalRemainingBalance(totalRemaining)
                .notes(gl.getNotes())
                .transactionDate(gl.getTransactionDate())
                .memberDetails(memberDetails)
                .createdAt(gl.getCreatedAt())
                .build();
    }
}
