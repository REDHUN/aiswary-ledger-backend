package com.redhun.aiswarya_ledger_api.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_path", nullable = false)
    private String requestPath;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_code", nullable = false)
    private Integer responseCode;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}
