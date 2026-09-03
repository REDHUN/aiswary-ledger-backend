package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByMemberNumber(String memberNumber);
    Optional<Member> findByPhone(String phone);
    Optional<Member> findByUserId(Long userId);
    List<Member> findByIsActiveTrueOrderByIdAsc();
    Page<Member> findByIsActiveTrue(Pageable pageable);
    boolean existsByMemberNumber(String memberNumber);
    boolean existsByPhone(String phone);

    @org.springframework.data.jpa.repository.Query(
            "SELECT m FROM Member m WHERE " +
                    "(:query IS NULL OR :query = '' " +
                    "OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                    "OR LOWER(m.memberNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
                    "OR LOWER(m.phone) LIKE LOWER(CONCAT('%', :query, '%'))) " +
                    "ORDER BY m.id ASC"
    )
    Page<Member> searchMembers(
            @org.springframework.data.repository.query.Param("query") String query,
            Pageable pageable
    );
}
