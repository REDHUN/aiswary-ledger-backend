package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import java.util.Optional;
import com.redhun.aiswarya_ledger_api.dto.request.CreateGroupProfitRequest;
import com.redhun.aiswarya_ledger_api.dto.response.GroupProfitDto;
import com.redhun.aiswarya_ledger_api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupProfitService {

    private final GroupProfitRepository groupProfitRepository;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Transactional(readOnly = true)
    public List<GroupProfitDto> getAllGroupProfits() {
        return groupProfitRepository.findAllByOrderByProfitDateDescIdDesc().stream()
                .map(this::mapProfitToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupProfitDto createGroupProfit(CreateGroupProfitRequest request, Long userId) {
        User operator = userId != null ? userRepository.findById(userId).orElse(null) : null;

        Meeting meeting = null;
        if (request.getMeetingId() != null) {
            meeting = meetingRepository.findById(request.getMeetingId()).orElse(null);
        } else {
            // Auto-link to currently OPEN or latest SCHEDULED meeting if not explicitly passed
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

        LocalDate prfDate = request.getProfitDate() != null ? request.getProfitDate() : LocalDate.now();

        GroupProfit profit = GroupProfit.builder()
                .title(request.getTitle().trim())
                .amount(request.getAmount())
                .profitDate(prfDate)
                .description(request.getDescription())
                .meeting(meeting)
                .createdBy(operator)
                .build();

        profit = groupProfitRepository.save(profit);

        // Add profit amount to Surplus Amount (Michha Thuka)
        BigDecimal currentSurplus = systemSettingService.getSurplusAmount();
        BigDecimal newSurplus = currentSurplus.add(request.getAmount());
        systemSettingService.updateSurplusAmount(newSurplus);

        // Update meeting surplus snapshot if set
        if (meeting != null && meeting.getSurplusAmount() != null) {
            meeting.setSurplusAmount(meeting.getSurplusAmount().add(request.getAmount()));
            meetingRepository.save(meeting);
        }

        // Log Financial Transaction on Surplus Reserve Fund
        Member operatorMember = memberRepository.findByUserId(operator != null ? operator.getId() : null)
                .orElseGet(() -> memberRepository.findByIsActiveTrueOrderByIdAsc().stream().findFirst().orElse(null));

        if (operatorMember != null) {
            String desc = "Group Profit: " + request.getTitle().trim() + (request.getDescription() != null && !request.getDescription().isBlank() ? " (" + request.getDescription() + ")" : "");
            
            FinancialTransaction tx = FinancialTransaction.builder()

                    .accountType(AccountType.GROUP_PROFIT)
                    .transactionType(TransactionType.ADDITION)
                    .amount(request.getAmount())
                    .balanceBefore(currentSurplus)
                    .balanceAfter(newSurplus)
                    .meeting(meeting)
                    .referenceType("GROUP_PROFIT")
                    .referenceId(profit.getId())
                    .description(desc)
                    .createdBy(operator)
                    .createdAt(ZonedDateTime.now())
                    .build();

            financialTransactionRepository.save(tx);
        }

        return mapProfitToDto(profit);
    }

    public GroupProfitDto mapProfitToDto(GroupProfit prof) {
        return GroupProfitDto.builder()
                .id(prof.getId())
                .title(prof.getTitle())
                .amount(prof.getAmount())
                .profitDate(prof.getProfitDate())
                .description(prof.getDescription())
                .meetingId(prof.getMeeting() != null ? prof.getMeeting().getId() : null)
                .createdAt(prof.getCreatedAt())
                .build();
    }
}
