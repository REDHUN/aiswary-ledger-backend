package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.*;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.dto.response.FinancialTransactionDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.FinancialTransactionRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberAccountRepository;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final MemberAccountRepository memberAccountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final MeetingRepository meetingRepository;

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
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Transaction amount must be greater than zero");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        Meeting meeting = null;
        if (meetingId != null) {
            meeting = meetingRepository.findById(meetingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Meeting", "id", meetingId));
        }

        // Fetch member account with PESSIMISTIC_WRITE row lock to protect against concurrent updates
        MemberAccount account = memberAccountRepository.findByMemberIdAndAccountTypeForUpdate(memberId, accountType)
                .orElseGet(() -> {
                    MemberAccount newAcc = MemberAccount.builder()
                            .member(member)
                            .accountType(accountType)
                            .currentBalance(BigDecimal.ZERO)
                            .version(0L)
                            .build();
                    return memberAccountRepository.save(newAcc);
                });

        BigDecimal balanceBefore = account.getCurrentBalance();
        BigDecimal balanceAfter;

        switch (transactionType) {
            case LOAN_ISSUED:
            case ADDITION:
            case INTEREST_APPLIED:
            case INITIAL_BALANCE:
                balanceAfter = balanceBefore.add(amount);
                break;
            case REPAYMENT:
                if (amount.compareTo(balanceBefore) > 0) {
                    throw new BusinessException(
                            "OVERPAYMENT_NOT_ALLOWED",
                            String.format("Repayment amount (₹%s) exceeds current balance (₹%s) for account category %s",
                                    amount, balanceBefore, accountType)
                    );
                }
                balanceAfter = balanceBefore.subtract(amount);
                break;
            case ADJUSTMENT:
                balanceAfter = balanceBefore.add(amount);
                if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("NEGATIVE_BALANCE_NOT_ALLOWED", "Account balance cannot become negative");
                }
                break;
            case REVERSAL:
                // Reversal restores balance
                balanceAfter = balanceBefore.add(amount);
                break;
            default:
                throw new BusinessException("UNSUPPORTED_TRANSACTION_TYPE", "Transaction type not supported");
        }

        // Update account balance
        account.setCurrentBalance(balanceAfter);
        memberAccountRepository.save(account);

        // Append ledger entry
        FinancialTransaction transaction = FinancialTransaction.builder()
                .member(member)
                .accountType(accountType)
                .transactionType(transactionType)
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

        return transactionRepository.save(transaction);
    }

    /**
     * Issues a new loan or adds to an existing loan balance.
     */
    @Transactional
    public FinancialTransactionDto issueLoan(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
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
                operator
        );
        return mapToDto(tx);
    }

    /**
     * Records a standalone deposit addition.
     */
    @Transactional
    public FinancialTransactionDto addDeposit(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
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
                operator
        );
        return mapToDto(tx);
    }

    /**
     * Records a fine addition.
     */
    @Transactional
    public FinancialTransactionDto addFine(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
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
                operator
        );
        return mapToDto(tx);
    }

    /**
     * Records a monthly contribution addition.
     */
    @Transactional
    public FinancialTransactionDto addMonthlyContribution(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
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
                operator
        );
        return mapToDto(tx);
    }

    /**
     * Records a financial aid addition.
     */
    @Transactional
    public FinancialTransactionDto addFinancialAid(Long memberId, BigDecimal amount, Long meetingId, String description, User operator) {
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

        // Determine opposite adjustment amount
        BigDecimal reversalAmount;
        if (originalTx.getTransactionType() == TransactionType.REPAYMENT) {
            // Reversing repayment adds amount back
            reversalAmount = originalTx.getAmount();
        } else {
            // Reversing addition/issuance subtracts amount
            reversalAmount = originalTx.getAmount().negate();
        }

        FinancialTransaction reversalTx = recordTransaction(
                originalTx.getMember().getId(),
                originalTx.getAccountType(),
                TransactionType.REVERSAL,
                originalTx.getAmount(),
                originalTx.getMeeting() != null ? originalTx.getMeeting().getId() : null,
                "REVERSAL_TARGET",
                originalTx.getId(),
                "Reversal of transaction #" + originalTx.getId() + ". Reason: " + reason,
                null,
                operator
        );

        return mapToDto(reversalTx);
    }

    @Transactional(readOnly = true)
    public Page<FinancialTransactionDto> getMemberTransactions(Long memberId, Pageable pageable) {
        if (!memberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("Member", "id", memberId);
        }
        return transactionRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable).map(this::mapToDto);
    }

    public FinancialTransactionDto mapToDto(FinancialTransaction tx) {
        return FinancialTransactionDto.builder()
                .id(tx.getId())
                .memberId(tx.getMember().getId())
                .memberName(tx.getMember().getFullName())
                .accountType(tx.getAccountType())
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
                .build();
    }
}
