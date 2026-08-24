package com.redhun.aiswarya_ledger_api.domain.entity;

import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_number", nullable = false, unique = true)
    private Integer meetingNumber;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(name = "interest_period", nullable = false, length = 7)
    private String interestPeriod; // 'YYYY-MM'

    @Column(name = "is_first_meeting_of_month", nullable = false)
    private Boolean isFirstMeetingOfMonth = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "opened_at")
    private ZonedDateTime openedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "surplus_amount")
    private BigDecimal surplusAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

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
