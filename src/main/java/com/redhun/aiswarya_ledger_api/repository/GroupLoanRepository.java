package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.GroupLoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupLoanRepository extends JpaRepository<GroupLoan, Long> {

    List<GroupLoan> findAllByOrderByCreatedAtDesc();
}
