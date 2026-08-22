package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.MeetingMember;
import com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, Long> {

    List<MeetingMember> findByMeetingId(Long meetingId);

    Optional<MeetingMember> findByMeetingIdAndMemberId(Long meetingId, Long memberId);

    long countByMeetingId(Long meetingId);

    long countByMeetingIdAndProcessingStatus(Long meetingId, MemberProcessingStatus processingStatus);

    @Query("SELECT mm FROM MeetingMember mm JOIN FETCH mm.member WHERE mm.meeting.id = :meetingId AND mm.processingStatus = :status")
    List<MeetingMember> findByMeetingIdAndProcessingStatusWithMember(
            @Param("meetingId") Long meetingId,
            @Param("status") MemberProcessingStatus status
    );
}
