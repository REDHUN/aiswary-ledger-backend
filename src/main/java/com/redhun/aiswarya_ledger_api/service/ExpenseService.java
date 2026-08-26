package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import java.util.Optional;
import com.redhun.aiswarya_ledger_api.dto.request.CreateExpenseTypeRequest;
import com.redhun.aiswarya_ledger_api.dto.request.CreateGroupExpenseRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ExpenseTypeDto;
import com.redhun.aiswarya_ledger_api.dto.response.GroupExpenseDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
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
public class ExpenseService {

    private final ExpenseTypeRepository expenseTypeRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Transactional(readOnly = true)
    public List<ExpenseTypeDto> getAllExpenseTypes() {
        return expenseTypeRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::mapTypeToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ExpenseTypeDto createExpenseType(CreateExpenseTypeRequest request) {
        if (expenseTypeRepository.findByNameIgnoreCase(request.getName().trim()).isPresent()) {
            throw new BusinessException("DUPLICATE_EXPENSE_TYPE", "Expense type with name '" + request.getName() + "' already exists");
        }

        ExpenseType type = ExpenseType.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .isActive(true)
                .build();

        type = expenseTypeRepository.save(type);
        return mapTypeToDto(type);
    }

    @Transactional
    public void deleteExpenseType(Long id) {
        ExpenseType type = expenseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseType", "id", id));
        type.setIsActive(false);
        expenseTypeRepository.save(type);
    }

    @Transactional(readOnly = true)
    public List<GroupExpenseDto> getAllGroupExpenses() {
        return groupExpenseRepository.findAllByOrderByExpenseDateDescIdDesc().stream()
                .map(this::mapExpenseToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupExpenseDto createGroupExpense(CreateGroupExpenseRequest request, Long userId) {
        ExpenseType type = expenseTypeRepository.findById(request.getExpenseTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseType", "id", request.getExpenseTypeId()));

        User operator = userId != null ? userRepository.findById(userId).orElse(null) : null;

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

        LocalDate expDate = request.getExpenseDate() != null ? request.getExpenseDate() : LocalDate.now();

        GroupExpense expense = GroupExpense.builder()
                .expenseType(type)
                .amount(request.getAmount())
                .expenseDate(expDate)
                .description(request.getDescription())
                .meeting(meeting)
                .createdBy(operator)
                .build();

        expense = groupExpenseRepository.save(expense);

        // Deduct expense amount from Surplus Amount
        BigDecimal currentSurplus = systemSettingService.getSurplusAmount();
        BigDecimal newSurplus = currentSurplus.subtract(request.getAmount());
        if (newSurplus.compareTo(BigDecimal.ZERO) < 0) {
            newSurplus = BigDecimal.ZERO;
        }
        systemSettingService.updateSurplusAmount(newSurplus);

        // Deduct from meeting surplus snapshot if set
        if (meeting != null && meeting.getSurplusAmount() != null) {
            BigDecimal mSurplus = meeting.getSurplusAmount().subtract(request.getAmount());
            if (mSurplus.compareTo(BigDecimal.ZERO) < 0) {
                mSurplus = BigDecimal.ZERO;
            }
            meeting.setSurplusAmount(mSurplus);
            meetingRepository.save(meeting);
        }

        // Log Financial Transaction directly on Surplus Reserve
        Member operatorMember = memberRepository.findByUserId(operator != null ? operator.getId() : null)
                .orElseGet(() -> memberRepository.findByIsActiveTrue().stream().findFirst().orElse(null));

        if (operatorMember != null) {
            String desc = "Group Expense: " + type.getName() + (request.getDescription() != null && !request.getDescription().isBlank() ? " (" + request.getDescription() + ")" : "");
            
            FinancialTransaction tx = FinancialTransaction.builder()
                    .member(operatorMember)
                    .accountType(AccountType.GROUP_EXPENSE)
                    .transactionType(TransactionType.REPAYMENT)
                    .amount(request.getAmount())
                    .balanceBefore(currentSurplus)
                    .balanceAfter(newSurplus)
                    .meeting(meeting)
                    .referenceType("GROUP_EXPENSE")
                    .referenceId(expense.getId())
                    .description(desc)
                    .createdBy(operator)
                    .createdAt(ZonedDateTime.now())
                    .build();

            financialTransactionRepository.save(tx);
        }

        return mapExpenseToDto(expense);
    }

    private ExpenseTypeDto mapTypeToDto(ExpenseType type) {
        return ExpenseTypeDto.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .isActive(type.getIsActive())
                .createdAt(type.getCreatedAt())
                .build();
    }

    private GroupExpenseDto mapExpenseToDto(GroupExpense exp) {
        return GroupExpenseDto.builder()
                .id(exp.getId())
                .expenseTypeId(exp.getExpenseType().getId())
                .expenseTypeName(exp.getExpenseType().getName())
                .amount(exp.getAmount())
                .expenseDate(exp.getExpenseDate())
                .description(exp.getDescription())
                .meetingId(exp.getMeeting() != null ? exp.getMeeting().getId() : null)
                .createdAt(exp.getCreatedAt())
                .build();
    }
}
