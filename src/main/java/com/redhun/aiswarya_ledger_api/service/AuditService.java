package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.AuditLog;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String entityName, Long entityId, String details) {
        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .entityName(entityName)
                .entityId(entityId)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }
}
