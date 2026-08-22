package com.redhun.aiswarya_ledger_api.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "member_number", nullable = false, unique = true, length = 20)
    private String memberNumber;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 15, unique = true)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
