package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
