package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.CreateMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.request.ProcessMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.request.ScheduleMeetingRequest;
import com.redhun.aiswarya_ledger_api.dto.response.MeetingDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberProcessingFormDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.InterestCalculationRequiredException;
import com.redhun.aiswarya_ledger_api.exception.MembersPendingException;
import com.redhun.aiswarya_ledger_api.repository.MeetingMemberRepository;
import com.redhun.aiswarya_ledger_api.repository.MeetingRepository;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class MeetingWorkflowTest {

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberProcessingService memberProcessingService;

    @Autowired
    private InterestService interestService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingMemberRepository meetingMemberRepository;

    private User adminUser;
    private MemberDto memberJohn;
    private MemberDto memberAnu;

    @BeforeEach
    public void setup() {
        meetingMemberRepository.deleteAll();
        meetingRepository.deleteAll();

        adminUser = userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("wfadmin")
                        .passwordHash("hash")
                        .role(com.redhun.aiswarya_ledger_api.domain.enums.UserRole.ADMIN)
                        .isActive(true)
                        .build())
        );

        CreateMemberRequest johnReq = new CreateMemberRequest();
        johnReq.setMemberNumber("WM001");
        johnReq.setFullName("John Workflow");
        johnReq.setJoiningDate(LocalDate.now());
        memberJohn = memberService.createMember(johnReq);

        CreateMemberRequest anuReq = new CreateMemberRequest();
        anuReq.setMemberNumber("WM002");
        anuReq.setFullName("Anu Workflow");
        anuReq.setJoiningDate(LocalDate.now());
        memberAnu = memberService.createMember(anuReq);

        // Give John a ₹20,000 loan
        ledgerService.issueLoan(memberJohn.getId(), new BigDecimal("20000.00"), null, "Initial Loan", adminUser);
    }

    @Test
    public void testUncompletedMeetingRejection() {
        ScheduleMeetingRequest scheduleReq = new ScheduleMeetingRequest();
        scheduleReq.setMeetingDate(LocalDate.of(2026, 10, 4));
        scheduleReq.setNotes("First Meeting");
        meetingService.scheduleMeeting(scheduleReq, adminUser);

        // Attempting to schedule a second meeting while first is SCHEDULED should throw UNCOMPLETED_MEETING_EXISTS
        ScheduleMeetingRequest req2 = new ScheduleMeetingRequest();
        req2.setMeetingDate(LocalDate.of(2026, 10, 11));
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            meetingService.scheduleMeeting(req2, adminUser);
        });
        assertEquals("UNCOMPLETED_MEETING_EXISTS", ex.getErrorCode());
    }

    @Test
    public void testFirstMeetingInterestBlockingAndCompletionWorkflow() {
        // Schedule and Open October 1st Meeting
        ScheduleMeetingRequest scheduleReq = new ScheduleMeetingRequest();
        scheduleReq.setMeetingDate(LocalDate.of(2026, 10, 4));
        scheduleReq.setNotes("First Meeting of October");
        MeetingDto meeting = meetingService.scheduleMeeting(scheduleReq, adminUser);

        MeetingDto openMeeting = meetingService.openMeeting(meeting.getId());
        assertTrue(openMeeting.getIsFirstMeetingOfMonth());

        // Attempt to process John WITHOUT calculating interest -> Should throw InterestCalculationRequiredException
        ProcessMemberRequest processJohnReq = new ProcessMemberRequest();
        processJohnReq.setLoanRepayment(new BigDecimal("5000.00"));

        assertThrows(InterestCalculationRequiredException.class, () -> {
            memberProcessingService.processMember(meeting.getId(), memberJohn.getId(), processJohnReq, adminUser);
        });

        // Calculate John's interest (₹20,000 * 1% = ₹200)
        interestService.calculateInterest(memberJohn.getId(), "2026-10", meeting.getId(), adminUser);

        // Now process John successfully
        MemberProcessingFormDto johnForm = memberProcessingService.processMember(meeting.getId(), memberJohn.getId(), processJohnReq, adminUser);
        assertEquals(new BigDecimal("15200.00"), johnForm.getLoanRemaining());

        // Attempt to complete meeting while Anu is still PENDING -> Should throw MembersPendingException
        MembersPendingException pendingEx = assertThrows(MembersPendingException.class, () -> {
            meetingService.completeMeeting(meeting.getId());
        });
        assertTrue(pendingEx.getMessage().contains("Anu Workflow"));

        // Process Anu with ZERO payment
        interestService.calculateInterest(memberAnu.getId(), "2026-10", meeting.getId(), adminUser);

        ProcessMemberRequest processAnuReq = new ProcessMemberRequest();
        memberProcessingService.processMember(meeting.getId(), memberAnu.getId(), processAnuReq, adminUser);

        // Process any remaining pending members snapshotted into meeting
        meetingService.getMeetingMembers(meeting.getId()).forEach(mm -> {
            if (mm.getProcessingStatus() == com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus.PENDING) {
                if (!interestService.isInterestCalculationRequired(mm.getMemberId(), "2026-10") ||
                    interestService.getMemberInterestHistory(mm.getMemberId()).stream().anyMatch(i -> i.getInterestPeriod().equals("2026-10"))) {
                    // already calculated
                } else {
                    interestService.calculateInterest(mm.getMemberId(), "2026-10", meeting.getId(), adminUser);
                }
                memberProcessingService.processMember(meeting.getId(), mm.getMemberId(), new ProcessMemberRequest(), adminUser);
            }
        });

        // Now complete meeting -> Should succeed and automatically schedule next Sunday meeting
        MeetingDto completedMeeting = meetingService.completeMeeting(meeting.getId());
        assertEquals(0L, completedMeeting.getPendingMembers());

        // Verify next meeting was auto-scheduled for next Sunday (2026-10-11)
        assertTrue(meetingService.getAllMeetings().stream()
                .anyMatch(m -> m.getMeetingDate().equals(LocalDate.of(2026, 10, 11))));
    }

    @Test
    public void testDuplicateInterestCalculationRejection() {
        ScheduleMeetingRequest scheduleReq = new ScheduleMeetingRequest();
        scheduleReq.setMeetingDate(LocalDate.of(2026, 11, 1));
        MeetingDto meeting = meetingService.scheduleMeeting(scheduleReq, adminUser);

        // Calculate interest once
        interestService.calculateInterest(memberJohn.getId(), "2026-11", meeting.getId(), adminUser);

        // Attempt second calculation for same month -> Should throw DUPLICATE_INTEREST_CALCULATION exception
        assertThrows(BusinessException.class, () -> {
            interestService.calculateInterest(memberJohn.getId(), "2026-11", meeting.getId(), adminUser);
        });
    }

    @Test
    public void testInterestTruncationRounding() {
        // Create a test member with a loan of ₹499.00
        CreateMemberRequest req = new CreateMemberRequest();
        req.setMemberNumber("WM499");
        req.setFullName("Truncate Test Member");
        req.setJoiningDate(LocalDate.now());
        MemberDto member = memberService.createMember(req);
        ledgerService.issueLoan(member.getId(), new BigDecimal("499.00"), null, "Test Loan", adminUser);

        ScheduleMeetingRequest scheduleReq = new ScheduleMeetingRequest();
        scheduleReq.setMeetingDate(LocalDate.of(2026, 12, 6));
        MeetingDto meeting = meetingService.scheduleMeeting(scheduleReq, adminUser);

        // Calculate 1% interest on ₹499.00 -> exact 4.99, rounded down (truncated) to ₹4.00
        var calc = interestService.calculateInterest(member.getId(), "2026-12", meeting.getId(), adminUser);
        assertEquals(new BigDecimal("4.00"), calc.getInterestAmount());
    }
}
