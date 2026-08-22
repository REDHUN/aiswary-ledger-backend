package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.FinancialTransaction;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    Page<FinancialTransaction> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    List<FinancialTransaction> findByMemberIdAndAccountTypeOrderByCreatedAtDesc(Long memberId, AccountType accountType);

    List<FinancialTransaction> findByMeetingId(Long meetingId);

    Optional<FinancialTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId AND t.transactionType = 'REPAYMENT'")
    BigDecimal sumMeetingRepayments(@Param("meetingId") Long meetingId);

    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t WHERE t.meeting.id = :meetingId")
    BigDecimal sumTotalMeetingCollections(@Param("meetingId") Long meetingId);
}
