package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.GroupExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface GroupExpenseRepository extends JpaRepository<GroupExpense, Long> {
    List<GroupExpense> findAllByOrderByExpenseDateDescIdDesc();
    List<GroupExpense> findByMeetingId(Long meetingId);

    @Query("SELECT SUM(g.amount) FROM GroupExpense g WHERE g.meeting.id = :meetingId")
    BigDecimal sumMeetingExpenses(@Param("meetingId") Long meetingId);
}
