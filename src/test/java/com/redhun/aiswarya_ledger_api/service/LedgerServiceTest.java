package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.FinancialTransaction;
import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import com.redhun.aiswarya_ledger_api.domain.enums.UserRole;
import com.redhun.aiswarya_ledger_api.dto.response.FinancialTransactionDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.repository.MemberRepository;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LedgerServiceTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    private Member testMember;
    private User adminUser;

    @BeforeEach
    public void setup() {
        adminUser = userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("ledgeradmin")
                        .passwordHash("hash")
                        .role(UserRole.ADMIN)
                        .isActive(true)
                        .build())
        );

        testMember = memberRepository.save(Member.builder()
                .memberNumber("LTESTM99")
                .fullName("Ledger Test Member")
                .joiningDate(LocalDate.now())
                .isActive(true)
                .build());

        // Initialize 6 account balances for test member
        for (AccountType accountType : AccountType.values()) {
            ledgerService.recordTransaction(
                    testMember.getId(),
                    accountType,
                    TransactionType.INITIAL_BALANCE,
                    new BigDecimal("0.01"),
                    null,
                    "INIT",
                    null,
                    "Initial setup",
                    null,
                    adminUser
            );
        }
    }

    @Test
    public void testLoanIssuanceAndAggregation() {
        // Issue ₹10,000 loan (starts from initial 0.01 balance -> 10000.01)
        FinancialTransactionDto tx1 = ledgerService.issueLoan(testMember.getId(), new BigDecimal("10000.00"), null, "First Loan", adminUser);
        assertEquals(new BigDecimal("10000.01"), tx1.getBalanceAfter());

        // Issue additional ₹5,000 loan (aggregates to ₹15,000.01)
        FinancialTransactionDto tx2 = ledgerService.issueLoan(testMember.getId(), new BigDecimal("5000.00"), null, "Additional Loan", adminUser);
        assertEquals(new BigDecimal("15000.01"), tx2.getBalanceAfter());
    }

    @Test
    public void testLoanRepaymentAndOverpaymentRejection() {
        // Issue ₹10,000 loan
        ledgerService.issueLoan(testMember.getId(), new BigDecimal("10000.00"), null, "Initial Loan", adminUser);

        // Repay ₹3,000
        FinancialTransaction tx = ledgerService.recordTransaction(
                testMember.getId(),
                AccountType.LOAN,
                TransactionType.REPAYMENT,
                new BigDecimal("3000.00"),
                null,
                "REPAYMENT",
                null,
                "Repayment",
                null,
                adminUser
        );
        assertEquals(new BigDecimal("7000.01"), tx.getBalanceAfter());

        // Attempt to repay ₹8,000 (exceeds ₹7,000.01 balance -> Should throw OVERPAYMENT_NOT_ALLOWED)
        assertThrows(BusinessException.class, () -> {
            ledgerService.recordTransaction(
                    testMember.getId(),
                    AccountType.LOAN,
                    TransactionType.REPAYMENT,
                    new BigDecimal("8000.00"),
                    null,
                    "REPAYMENT",
                    null,
                    "Overpayment attempt",
                    null,
                    adminUser
            );
        });
    }

    @Test
    public void testFinancialReversal() {
        // Issue ₹5,000 loan
        FinancialTransactionDto tx = ledgerService.issueLoan(testMember.getId(), new BigDecimal("5000.00"), null, "Loan", adminUser);

        // Reverse loan issuance
        FinancialTransactionDto reversal = ledgerService.reverseTransaction(tx.getId(), "Mistake", adminUser);
        assertEquals(TransactionType.REVERSAL, reversal.getTransactionType());
    }
}
