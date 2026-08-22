package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.InterestCalculation;
import com.redhun.aiswarya_ledger_api.domain.enums.InterestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterestCalculationRepository extends JpaRepository<InterestCalculation, Long> {

    Optional<InterestCalculation> findByMemberIdAndInterestPeriod(Long memberId, String interestPeriod);

    boolean existsByMemberIdAndInterestPeriod(Long memberId, String interestPeriod);

    List<InterestCalculation> findByInterestPeriod(String interestPeriod);

    List<InterestCalculation> findByMemberIdOrderByCalculatedAtDesc(Long memberId);

    List<InterestCalculation> findByMemberIdAndStatusIn(Long memberId, List<InterestStatus> statuses);

    long countByInterestPeriod(String interestPeriod);

    long countByInterestPeriodAndStatus(String interestPeriod, InterestStatus status);
}
