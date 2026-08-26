package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.GroupProfit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface GroupProfitRepository extends JpaRepository<GroupProfit, Long> {
    List<GroupProfit> findByMeetingId(Long meetingId);
    List<GroupProfit> findAllByOrderByProfitDateDescIdDesc();

    @Query("SELECT SUM(gp.amount) FROM GroupProfit gp WHERE gp.meeting.id = :meetingId")
    BigDecimal sumMeetingProfits(@Param("meetingId") Long meetingId);
}
