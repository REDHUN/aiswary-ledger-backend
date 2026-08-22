package com.redhun.aiswarya_ledger_api.domain.entity;

import com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(
    name = "meeting_members",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_meeting_member", columnNames = {"meeting_id", "member_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private MemberProcessingStatus processingStatus = MemberProcessingStatus.PENDING;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;
}
