package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.MemberAccount;
import com.redhun.aiswarya_ledger_api.domain.enums.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberAccountRepository extends JpaRepository<MemberAccount, Long> {

    List<MemberAccount> findByMemberId(Long memberId);

    Optional<MemberAccount> findByMemberIdAndAccountType(Long memberId, AccountType accountType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ma FROM MemberAccount ma WHERE ma.member.id = :memberId AND ma.accountType = :accountType")
    Optional<MemberAccount> findByMemberIdAndAccountTypeForUpdate(
            @Param("memberId") Long memberId,
            @Param("accountType") AccountType accountType
    );
}
