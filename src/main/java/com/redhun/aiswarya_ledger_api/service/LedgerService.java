package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.dto.response.FinancialTransactionDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.FinancialTransactionRepository;
import com.redhun.aiswarya_ledger_api.repository.InterestCalculationRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberAccountRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final MemberRepository memberRepository;
    private final MemberAccountRepository memberAccountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final MeetingRepository meetingRepository;
    private final InterestCalculationRepository interestCalculationRepository;
    private final com.redhun.aiswarya_ledger_api.repository.SpecialLoanTypeRepository specialLoanTypeRepository;
    private final SystemSettingService systemSettingService;

    /**
     * Executes a financial account change under pessimistic write lock, appending a transaction to the audit ledger.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FinancialTransaction recordTransaction(
            Long memberId,
            AccountType accountType,
            TransactionType transactionType,
            BigDecimal amount,
            Long meetingId,
            String referenceType,
            Long referenceId,
            String description,
            String idempotencyKey,
            User operator
    ) {
        return recordTransaction(memberId, accountType, transactionType, amount, meetingId, referenceType, referenceId, description, idempotencyKey, null, null, operator);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FinancialTransaction recordTransaction(
            Long memberId,
            AccountType accountType,
            TransactionType transactionType,
            BigDecimal amount,
            Long meetingId,
            String referenceType,
            Long referenceId,
            String description,
            String idempotencyKey,
            LocalDate transactionDate,
            User operator
    ) {
        return recordTransaction(memberId, accountType, transactionType, amount, meetingId, referenceType, referenceId, description, idempotencyKey, transactionDate, null, operator);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FinancialTransaction recordTransaction(
            Long memberId,
            AccountType accountType,
            TransactionType transactionType,
            BigDecimal amount,
            Long meetingId,
            String referenceType,
            Long referenceId,
            String description,
            String idempotencyKey,
            LocalDate transactionDate,
            SpecialLoanType specialLoanType,
            User operator
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Transaction amount must be greater than zero");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        Meeting meeting = null;
        if (accountType == AccountType.SPECIAL_LOAN && transactionType == TransactionType.LOAN_ISSUED) {
            meeting = null;
        } else if (accountType == AccountType.FINE && transactionType == TransactionType.ADDITION) {
            meeting = null;
        } else if (meetingId != null) {
            meeting = meetingRepository.findById(meetingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));
        } else if (accountType != AccountType.SPECIAL_LOAN) {
            // Auto-link to currently OPEN meeting, or SCHEDULED meeting if no OPEN meeting exists (except for SPECIAL_LOAN and FINE addition)
            meeting = meetingRepository.findTop1ByStatusOrderByMeetingDateDescMeetingNumberDesc(com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus.OPEN)
                    .orElseGet(() -> meetingRepository.findTop1ByStatusOrderByMeetingDateDescMeetingNumberDesc(com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus.SCHEDULED)
                            .orElse(null));
        }


        if (accountType != AccountType.FINANCIAL_AID) {



            // Deduct expense amount from Surplus Amount
            BigDecimal currentSurplus = systemSettingService.getSurplusAmount();
            BigDecimal newSurplus = currentSurplus.subtract(amount);
            if (newSurplus.compareTo(BigDecimal.ZERO) < 0) {
                newSurplus = BigDecimal.ZERO;
            }
            systemSettingService.updateSurplusAmount(newSurplus);

            // Deduct from meeting surplus snapshot if set
            if (meeting != null && meeting.getSurplusAmount() != null) {
                BigDecimal mSurplus = meeting.getSurplusAmount().subtract(amount);
                if (mSurplus.compareTo(BigDecimal.ZERO) < 0) {
                    mSurplus = BigDecimal.ZERO;
                }
                meeting.setSurplusAmount(mSurplus);
                meetingRepository.save(meeting);
            }



        }





        Long specialLoanTypeId = specialLoanType != null ? specialLoanType.getId() : null;

        // Fetch member account with PESSIMISTIC_WRITE row lock to protect against concurrent updates
        MemberAccount account = memberAccountRepository.findByMemberIdAndAccountTypeAndSpecialLoanTypeForUpdate(memberId, accountType, specialLoanTypeId)
                .orElseGet(() -> {
                    MemberAccount newAcc = MemberAccount.builder()
                            .member(member)
                            .accountType(accountType)
                            .specialLoanType(specialLoanType)
                            .currentBalance(BigDecimal.ZERO)
                            .version(0L)
                            .build();
                    return memberAccountRepository.save(newAcc);
                });

        BigDecimal balanceBefore = account.getCurrentBalance();
        TransactionType effectiveTransactionType = transactionType;

        if (accountType == AccountType.DEPOSIT && transactionType == TransactionType.ADDITION && (balanceBefore == null || balanceBefore.compareTo(BigDecimal.ZERO) == 0)) {
            effectiveTransactionType = TransactionType.INITIAL_BALANCE;
        }

        BigDecimal balanceAfter;

        switch (effectiveTransactionType) {
            case LOAN_ISSUED:
            case ADDITION:
            case INTEREST_APPLIED:
            case INITIAL_BALANCE:
                balanceAfter = balanceBefore.add(amount);
                break;
            case REPAYMENT:
                if (accountType == AccountType.FINE) {
                    if (balanceBefore == null || balanceBefore.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(
                                "NO_FINE_BALANCE",
                                "Cannot repay fine because member has no outstanding fine balance (Current fine is ₹0.00)"
                        );
                    }
                    if (amount.compareTo(balanceBefore) > 0) {
                        throw new BusinessException(
                                "OVERPAYMENT_NOT_ALLOWED",
                                String.format("Fine payment amount (₹%s) exceeds current fine balance (₹%s)",
                                        amount, balanceBefore)
                        );
                    }
                    balanceAfter = balanceBefore.subtract(amount);
                } else if (amount.compareTo(balanceBefore) > 0) {
                    throw new BusinessException(
                            "OVERPAYMENT_NOT_ALLOWED",
                            String.format("Repayment amount (₹%s) exceeds current balance (₹%s) for account category %s",
                                    amount, balanceBefore, specialLoanType != null ? specialLoanType.getName() : accountType)
                    );
                } else {
                    balanceAfter = balanceBefore.subtract(amount);
                }
                break;
            case ADJUSTMENT:
                balanceAfter = balanceBefore.add(amount);
                if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("NEGATIVE_BALANCE_NOT_ALLOWED", "Account balance cannot become negative");
                }
                break;
            case REVERSAL:
                balanceAfter = balanceBefore.add(amount);
                break;
            default:
                throw new BusinessException("UNSUPPORTED_TRANSACTION_TYPE", "Transaction type " + transactionType + " is not supported");
        }

        // Update account balance
        account.setCurrentBalance(balanceAfter);
        memberAccountRepository.save(account);

        FinancialTransaction tx = FinancialTransaction.builder()
                .member(member)
                .accountType(accountType)
                .specialLoanType(specialLoanType)
                .transactionType(effectiveTransactionType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .meeting(meeting)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .createdBy(operator)
                .build();

        if (transactionDate != null) {
            if (transactionDate.equals(LocalDate.now())) {
                tx.setCreatedAt(ZonedDateTime.now());
            } else {
                tx.setCreatedAt(transactionDate.atTime(java.time.LocalTime.now()).atZone(ZoneId.systemDefault()));
            }
        }

        return transactionRepository.save(tx);
    }

    /**
     * Issues a new loan or adds to an existing loan balance.
     */
    @Transactional
    public FinancialTransactionDto issueLoan(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
        return issueLoan(memberId, amount, meetingId, description, null, operator);
    }

//    @Transactional
//    public FinancialTransactionDto addDeposit(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
//        return addDeposit(memberId, amount, meetingId, description, null, operator);
//    }
//
//    @Transactional
//    public FinancialTransactionDto addFine(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
//        return addFine(memberId, amount, meetingId, description, null, operator);
//    }
//
//    @Transactional
//    public FinancialTransactionDto addMonthlyContribution(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
//        return addMonthlyContribution(memberId, amount, meetingId, description, null, operator);
//    }
//
//    @Transactional
//    public FinancialTransactionDto addFinancialAid(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
//        return addFinancialAid(memberId, amount, meetingId, description, null, operator);
//    }

    @Transactional
    public FinancialTransactionDto issueLoan(Long memberId, BigDecimal amount, Long meetingId, String description, LocalDate transactionDate, User operator) {
        FinancialTransaction tx = recordTransaction(
                memberId,
                AccountType.LOAN,
                TransactionType.LOAN_ISSUED,
                amount,
                meetingId,
                "LOAN_ISSUANCE",
                null,
                description != null ? description : "Loan issued",
                null,
                transactionDate,
                operator
        );
        return mapToDto(tx);
    }

    /**
     * Records a standalone deposit addition.
     */
    @Transactional
    public FinancialTransactionDto addDeposit(Long memberId, BigDecimal amount, Long meetingId, String description, LocalDate transactionDate, User operator) {
        FinancialTransaction tx = recordTransaction(
                memberId,
                AccountType.DEPOSIT,
                TransactionType.ADDITION,
                amount,
                meetingId,
                "DIRECT_DEPOSIT",
                null,
                description != null ? description : "Deposit recorded",
                null,
                transactionDate,
                operator
        );

        return mapToDto(tx);
    }

    /**
     * Records a fine addition.
     */
    @Transactional
    public FinancialTransactionDto addFine(Long memberId, BigDecimal amount, Long meetingId, String description, LocalDate transactionDate, User operator) {
        FinancialTransaction tx = recordTransaction(
                memberId,
                AccountType.FINE,
                TransactionType.ADDITION,
                amount,
                meetingId,
                "FINE_IMPOSED",
                null,
                description != null ? description : "Fine recorded",
                null,
                transactionDate,
                operator
        );
        return mapToDto(tx);
    }

    /**
     * Records a monthly contribution addition.
     */
    @Transactional
    public FinancialTransactionDto addMonthlyContribution(Long memberId, BigDecimal amount, Long meetingId, String description, LocalDate transactionDate, User operator) {
        FinancialTransaction tx = recordTransaction(
                memberId,
                AccountType.MONTHLY_CONTRIBUTION,
                TransactionType.ADDITION,
                amount,
                meetingId,
                "MONTHLY_CONTRIBUTION",
                null,
                description != null ? description : "Monthly contribution recorded",
                null,
                transactionDate,
                operator
        );
        return mapToDto(tx);
    }

    /**
     * Records a financial aid addition.
     */
    @Transactional
    public FinancialTransactionDto addFinancialAid(Long memberId, BigDecimal amount, Long meetingId, String description, LocalDate transactionDate, User operator) {



        FinancialTransaction tx = recordTransaction(
                memberId,
                AccountType.FINANCIAL_AID,
                TransactionType.ADDITION,
                amount,
                meetingId,
                "FINANCIAL_AID",
                null,
                description != null ? description : "Financial aid recorded",
                null,
                transactionDate,
                operator
        );




        return mapToDto(tx);
    }

    /**
     * Financial correction / reversal mechanism.
     */
    @Transactional
    public FinancialTransactionDto reverseTransaction(Long transactionId, String reason, User operator) {
        FinancialTransaction originalTx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (originalTx.getTransactionType() == TransactionType.REVERSAL) {
            throw new BusinessException("CANNOT_REVERSE_REVERSAL", "Cannot reverse an already reversed transaction");
        }

        if (Boolean.TRUE.equals(originalTx.getIsReversed())) {
            throw new BusinessException("TRANSACTION_ALREADY_REVERSED", "Transaction #" + transactionId + " has already been reversed");
        }

        // Fetch account with PESSIMISTIC_WRITE lock
        MemberAccount account = memberAccountRepository.findByMemberIdAndAccountTypeAndSpecialLoanTypeForUpdate(
                originalTx.getMember().getId(),
                originalTx.getAccountType(),
                originalTx.getSpecialLoanType() != null ? originalTx.getSpecialLoanType().getId() : null
        ).orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));

        BigDecimal balanceBefore = account.getCurrentBalance();
        BigDecimal balanceAfter;

        if (originalTx.getTransactionType() == TransactionType.REPAYMENT) {
            // Reversing a repayment ADDS amount back to balance
            balanceAfter = balanceBefore.add(originalTx.getAmount());
        } else {
            // Reversing an addition / loan issuance / interest calculation SUBTRACTS amount from balance
            if (originalTx.getAmount().compareTo(balanceBefore) > 0) {
                throw new BusinessException("REVERSAL_EXCEEDS_BALANCE", "Cannot reverse transaction because current account balance is less than transaction amount");
            }
            balanceAfter = balanceBefore.subtract(originalTx.getAmount());
        }

        account.setCurrentBalance(balanceAfter);
        memberAccountRepository.save(account);

        // Mark original transaction as reversed
        originalTx.setIsReversed(true);
        transactionRepository.save(originalTx);

        FinancialTransaction reversalTx = FinancialTransaction.builder()
                .member(originalTx.getMember())
                .accountType(originalTx.getAccountType())
                .transactionType(TransactionType.REVERSAL)
                .amount(originalTx.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .meeting(originalTx.getMeeting())
                .referenceType("REVERSAL_TARGET")
                .referenceId(originalTx.getId())
                .description("Reversal of transaction #" + originalTx.getId() + ". Reason: " + reason)
                .createdBy(operator)
                .build();

        reversalTx = transactionRepository.save(reversalTx);

        // If reversing an interest calculation, unlock the interest calculation record for that period
        if ("INTEREST_CALCULATION".equals(originalTx.getReferenceType()) && originalTx.getReferenceId() != null) {
            interestCalculationRepository.deleteById(originalTx.getReferenceId());
        }

        return mapToDto(reversalTx);
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransactionDto> getMemberTransactions(Long memberId, Pageable pageable) {
        if (!memberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("Member", "id", memberId);
        }
        return transactionRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionDto> getRecentTransactions() {
        return transactionRepository.findTop5ByOrderByCreatedAtDescIdDesc().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransactionDto> getAllTransactions(Pageable pageable) {
        return getAllTransactions(null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransactionDto> getAllTransactions(
            String query,
            AccountType accountType,
            TransactionType transactionType,
            Boolean isReversed,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        var spec = com.redhun.aiswarya_ledger_api.repository.specification.FinancialTransactionSpecification
                .getFilterSpecification(query, accountType, transactionType, isReversed, startDate, endDate);

        return transactionRepository.findAll(spec, pageable).map(this::mapToDto);
    }

    public FinancialTransactionDto mapToDto(FinancialTransaction tx) {
        return FinancialTransactionDto.builder()
                .id(tx.getId())
                .memberId(tx.getMember() != null ? tx.getMember().getId() : null)
                .memberName(tx.getMember() != null ? tx.getMember().getFullName() : "Group Account")
                .accountType(tx.getAccountType())
                .specialLoanTypeId(tx.getSpecialLoanType() != null ? tx.getSpecialLoanType().getId() : null)
                .specialLoanTypeName(tx.getSpecialLoanType() != null ? tx.getSpecialLoanType().getName() : null)
                .transactionType(tx.getTransactionType())
                .amount(tx.getAmount())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .meetingId(tx.getMeeting() != null ? tx.getMeeting().getId() : null)
                .referenceType(tx.getReferenceType())
                .referenceId(tx.getReferenceId())
                .description(tx.getDescription())
                .createdByUsername(tx.getCreatedBy() != null ? tx.getCreatedBy().getUsername() : null)
                .createdAt(tx.getCreatedAt())
                .isReversed(Boolean.TRUE.equals(tx.getIsReversed()))
                .build();
    }
}
