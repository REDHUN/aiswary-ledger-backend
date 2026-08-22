package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByMemberNumber(String memberNumber);
    Optional<Member> findByUserId(Long userId);
    List<Member> findByIsActiveTrue();
    Page<Member> findByIsActiveTrue(Pageable pageable);
    boolean existsByMemberNumber(String memberNumber);
    boolean existsByPhone(String phone);
}
