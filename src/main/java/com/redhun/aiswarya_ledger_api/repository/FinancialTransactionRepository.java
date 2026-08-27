package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.FinancialTransaction;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import com.redhun.aiswarya_ledger_api.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long>, JpaSpecificationExecutor<FinancialTransaction> {

    Page<FinancialTransaction> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    List<FinancialTransaction> findByMemberIdAndAccountTypeOrderByCreatedAtDescIdDesc(Long memberId, AccountType accountType);

    List<FinancialTransaction> findByMeetingId(Long meetingId);

    List<FinancialTransaction> findTop5ByOrderByCreatedAtDescIdDesc();

    Page<FinancialTransaction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Optional<FinancialTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId AND (t.isReversed IS NULL OR t.isReversed = false) AND t.transactionType != :reversalType AND t.accountType != :aidType AND t.transactionType != :interestType AND t.transactionType != com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.LOAN_ISSUED AND (t.referenceType IS NULL OR (t.referenceType != 'MEETING_SURPLUS_TRANSFER' AND t.referenceType != 'SURPLUS_FUND_ADDITION'))")
    BigDecimal sumMeetingCollectionsExcludingAid(
            @Param("meetingId") Long meetingId,
            @Param("reversalType") TransactionType reversalType,
            @Param("aidType") AccountType aidType,
            @Param("interestType") TransactionType interestType
    );

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId AND (t.isReversed IS NULL OR t.isReversed = false) AND t.transactionType != :reversalType AND t.accountType = :aidType")
    BigDecimal sumMeetingFinancialAid(
            @Param("meetingId") Long meetingId,
            @Param("reversalType") TransactionType reversalType,
            @Param("aidType") AccountType aidType
    );

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE (t.isReversed IS NULL OR t.isReversed = false) AND t.transactionType IN (com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.REPAYMENT, com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.ADDITION) AND t.accountType != com.redhun.aiswarya_ledger_api.domain.enums.AccountType.FINANCIAL_AID")
    BigDecimal sumAllCollections();

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE (t.isReversed IS NULL OR t.isReversed = false) AND t.accountType = com.redhun.aiswarya_ledger_api.domain.enums.AccountType.FINANCIAL_AID AND t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.ADDITION")
    BigDecimal sumAllFinancialAidIssued();

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId AND (t.isReversed IS NULL OR t.isReversed = false) AND t.accountType = :accountType AND (t.transactionType = :transactionType OR t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.ADDITION OR t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.INITIAL_BALANCE OR (t.accountType = com.redhun.aiswarya_ledger_api.domain.enums.AccountType.FINE AND (t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.ADDITION OR t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.REPAYMENT))) AND (t.referenceType IS NULL OR (t.referenceType != 'MEETING_SURPLUS_TRANSFER' AND t.referenceType != 'SURPLUS_FUND_ADDITION'))")
    BigDecimal sumMeetingCategory(
            @Param("meetingId") Long meetingId,
            @Param("accountType") AccountType accountType,
            @Param("transactionType") TransactionType transactionType
    );

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId AND (t.isReversed IS NULL OR t.isReversed = false) AND t.transactionType != 'REVERSAL' AND (t.referenceType IS NULL OR (t.referenceType != 'MEETING_SURPLUS_TRANSFER' AND t.referenceType != 'SURPLUS_FUND_ADDITION'))")
    BigDecimal sumTotalMeetingCollections(@Param("meetingId") Long meetingId);

    @Query("SELECT t.specialLoanType.id, t.specialLoanType.name, SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId AND (t.isReversed IS NULL OR t.isReversed = false) AND t.accountType = com.redhun.aiswarya_ledger_api.domain.enums.AccountType.SPECIAL_LOAN AND t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.REPAYMENT GROUP BY t.specialLoanType.id, t.specialLoanType.name")
    List<Object[]> sumMeetingSpecialLoansByType(@Param("meetingId") Long meetingId);

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId AND (t.isReversed IS NULL OR t.isReversed = false) AND t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.LOAN_ISSUED")
    BigDecimal sumMeetingLoansIssued(@Param("meetingId") Long meetingId);

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE (t.isReversed IS NULL OR t.isReversed = false) AND t.transactionType = com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.LOAN_ISSUED")
    BigDecimal sumAllLoansIssued();


    List<FinancialTransaction> findByMeetingIdAndTransactionTypeAndIsReversedFalse(Long meetingId, TransactionType transactionType);

}
