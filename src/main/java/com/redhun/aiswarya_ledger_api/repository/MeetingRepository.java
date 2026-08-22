package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Optional<Meeting> findByMeetingNumber(Integer meetingNumber);

    List<Meeting> findByStatus(MeetingStatus status);

    List<Meeting> findByStatusIn(List<MeetingStatus> statuses);

    Optional<Meeting> findFirstByStatusOrderByMeetingDateAsc(MeetingStatus status);

    Optional<Meeting> findFirstByOrderByMeetingNumberDesc();

    boolean existsByMeetingDate(LocalDate meetingDate);

    List<Meeting> findAllByOrderByMeetingDateAscMeetingNumberAsc();

    List<Meeting> findAllByOrderByMeetingDateDescMeetingNumberDesc();

    @Query("SELECT COUNT(m) > 0 FROM Meeting m WHERE m.interestPeriod = :period AND m.status != 'CANCELLED'")
    boolean existsActiveMeetingInPeriod(@Param("period") String period);

    @Query("SELECT m FROM Meeting m WHERE m.interestPeriod = :period AND m.status != 'CANCELLED' ORDER BY m.meetingDate ASC, m.meetingNumber ASC")
    List<Meeting> findMeetingsInPeriodSorted(@Param("period") String period);
}
