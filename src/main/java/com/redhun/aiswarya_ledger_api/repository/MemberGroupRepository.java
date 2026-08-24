package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.MemberGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberGroupRepository extends JpaRepository<MemberGroup, Long> {

    List<MemberGroup> findByIsActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT mg FROM MemberGroup mg LEFT JOIN FETCH mg.members WHERE mg.id = :id")
    MemberGroup findByIdWithMembers(@Param("id") Long id);
}
