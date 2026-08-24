package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.SpecialLoanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialLoanTypeRepository extends JpaRepository<SpecialLoanType, Long> {
    List<SpecialLoanType> findByIsActiveTrue();
    Optional<SpecialLoanType> findByNameIgnoreCase(String name);
}
