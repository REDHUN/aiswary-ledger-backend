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
import java.util.List;

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
    private com.redhun.aiswarya_ledger_api.repository.InterestCalculationRepository interestCalculationRepository;

    @Autowired
    private com.redhun.aiswarya_ledger_api.repository.MemberRepository memberRepository;

    @Autowired
    private com.redhun.aiswarya_ledger_api.service.ReportService reportService;

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

    @Test
    public void testInterestReversalAndRecalculationUnlock() {
        CreateMemberRequest req = new CreateMemberRequest();
        req.setMemberNumber("WMREV");
        req.setFullName("Reversal Test Member");
        req.setJoiningDate(LocalDate.now());
        MemberDto member = memberService.createMember(req);
        ledgerService.issueLoan(member.getId(), new BigDecimal("10000.00"), null, "Test Loan", adminUser);

        ScheduleMeetingRequest scheduleReq = new ScheduleMeetingRequest();
        scheduleReq.setMeetingDate(LocalDate.of(2027, 1, 3));
        MeetingDto meeting = meetingService.scheduleMeeting(scheduleReq, adminUser);

        // Calculate interest for 2027-01 -> 1% of 10,000 = ₹100.00 added to loan (loan balance = 10,100)
        interestService.calculateInterest(member.getId(), "2027-01", meeting.getId(), adminUser);

        // Fetch interest calculation transaction
        var recentTx = ledgerService.getRecentTransactions().stream()
                .filter(t -> t.getMemberId().equals(member.getId()) && t.getTransactionType() == com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.INTEREST_APPLIED)
                .findFirst().orElseThrow();

        // Reverse the interest transaction
        ledgerService.reverseTransaction(recentTx.getId(), "Accidental interest calculation", adminUser);

        // Verify second reversal attempt is rejected
        assertThrows(BusinessException.class, () -> {
            ledgerService.reverseTransaction(recentTx.getId(), "Duplicate reversal attempt", adminUser);
        });

        // Verify interest period is unlocked (isInterestCalculationRequired == true)
        assertTrue(interestService.isInterestCalculationRequired(member.getId(), "2027-01"));
    }

    @Test
    public void testCustomTransactionDateRecording() {
        CreateMemberRequest req = new CreateMemberRequest();
        req.setMemberNumber("WMDATE");
        req.setFullName("Date Test Member");
        MemberDto member = memberService.createMember(req);

        LocalDate customDate = LocalDate.of(2026, 8, 15);
        var tx = ledgerService.issueLoan(member.getId(), new BigDecimal("5000.00"), null, "Custom Date Loan", customDate, adminUser);

        assertEquals(customDate, tx.getCreatedAt().toLocalDate());
    }

    @Autowired
    private ImportService importService;

    @Test
    public void testBulkImport1YearLedgerData() {
        com.redhun.aiswarya_ledger_api.dto.request.BulkImportItemDto item1 = com.redhun.aiswarya_ledger_api.dto.request.BulkImportItemDto.builder()
                .memberNumber("IMP001")
                .fullName("Imported Member")
                .accountType(com.redhun.aiswarya_ledger_api.domain.enums.AccountType.LOAN)
                .transactionType(com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.LOAN_ISSUED)
                .amount(new BigDecimal("12000.00"))
                .transactionDate(LocalDate.of(2025, 1, 15))
                .description("Opening 2025 Loan")
                .build();

        com.redhun.aiswarya_ledger_api.dto.request.BulkImportItemDto item2 = com.redhun.aiswarya_ledger_api.dto.request.BulkImportItemDto.builder()
                .memberNumber("IMP001")
                .accountType(com.redhun.aiswarya_ledger_api.domain.enums.AccountType.LOAN)
                .transactionType(com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.REPAYMENT)
                .amount(new BigDecimal("1000.00"))
                .transactionDate(LocalDate.of(2025, 2, 15))
                .description("Feb Repayment")
                .build();

        com.redhun.aiswarya_ledger_api.dto.request.BulkImportRequest importReq = new com.redhun.aiswarya_ledger_api.dto.request.BulkImportRequest();
        importReq.setItems(java.util.List.of(item1, item2));

        var response = importService.importBulkTransactions(importReq, adminUser);
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getErrorCount());
    }

    @Test
    public void testImportCsvFile() {
        String csvContent = "memberNumber,fullName,accountType,transactionType,amount,transactionDate,description\n" +
                "CSV001,CSV Member,LOAN,LOAN_ISSUED,15000,2025-01-10,CSV Loan\n" +
                "CSV001,CSV Member,LOAN,REPAYMENT,1500,2025-02-10,CSV Repayment\n";

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "ledger.csv",
                "text/csv",
                csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        var response = importService.importCsvFile(file, adminUser);
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getErrorCount());
    }

    @Test
    public void testFlexibleDateFormatParsing() {
        String csvContent = "memberNumber,fullName,accountType,transactionType,amount,transactionDate,description\n" +
                "FLEX01,Flex Member,LOAN,LOAN_ISSUED,10000,04-01-2026,DD-MM-YYYY Loan\n" +
                "FLEX01,Flex Member,LOAN,REPAYMENT,1000,04/02/2026,DD/MM/YYYY Repayment\n";

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "ledger_data.csv",
                "text/csv",
                csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        var response = importService.importCsvFile(file, adminUser);
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getErrorCount());
    }

    @Test
    public void testImportedInterestPreventsDuplicateCalculationRequirement() {
        String csvContent = "memberNumber,fullName,accountType,transactionType,amount,transactionDate,description\n" +
                "IMPINT01,Import Interest Member,LOAN,LOAN_ISSUED,10000,2026-08-01,Loan Issued\n" +
                "IMPINT01,Import Interest Member,LOAN,INTEREST_APPLIED,100,2026-08-15,Aug 1% Interest\n";

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "ledger_data.csv",
                "text/csv",
                csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        importService.importCsvFile(file, adminUser);

        com.redhun.aiswarya_ledger_api.domain.entity.Member member = memberRepository.findByMemberNumber("IMPINT01").orElseThrow();

                                                assertFalse(interestService.isInterestCalculationRequired(member.getId(), "2026-08"));
    }


    @Test
    public void testImport5Members10MeetingsCsv() throws Exception {
        java.io.File fileOnDisk = new java.io.File("aiswarya_ledger_5members_10meetings.csv");
        assertTrue(fileOnDisk.exists(), "CSV file should exist in project root");

        byte[] content = java.nio.file.Files.readAllBytes(fileOnDisk.toPath());
        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "aiswarya_ledger_5members_10meetings.csv",
                "text/csv",
                content
        );

        var response = importService.importCsvFile(mockFile, adminUser);
        assertNotNull(response);
        System.out.println("Import Result: Total=" + response.getTotalProcessed() + ", Success=" + response.getSuccessCount() + ", Errors=" + response.getErrorCount() + " -> " + response.getErrors());
        assertTrue(response.getSuccessCount() > 0, "Should successfully import rows: successCount=" + response.getSuccessCount());
        assertEquals(0, response.getErrorCount(), "Should have 0 errors during import: " + response.getErrors());

        // Verify 10 meetings created
        com.redhun.aiswarya_ledger_api.domain.entity.Meeting m10 = meetingRepository.findByMeetingNumber(10).orElseThrow();
        assertNotNull(m10);

        // Verify member count
        var allMembers = memberRepository.findAll();
        assertTrue(allMembers.size() >= 5, "Should have created at least 5 members");

        // Verify meeting report for meeting #10
        var m10Report = reportService.getMeetingReport(m10.getId());
        assertNotNull(m10Report);
                assertTrue(m10Report.getMeetingNumber() == 10);
    }

    @Test
    public void testFinancialReportsGeneration() {
        var summary = reportService.getFinancialSummary();
        assertNotNull(summary);
        assertTrue(summary.getTotalMembers() >= 0);

        var periodReport = reportService.getPeriodReport(java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now());
        assertNotNull(periodReport);

        var memberBalances = reportService.getMemberBalancesReport();
        assertNotNull(memberBalances);
    }

    @Test
    public void testMeetingReportGeneration() {
        var meetingList = meetingRepository.findAll();
        if (!meetingList.isEmpty()) {
            Long meetingId = meetingList.get(0).getId();
            var report = reportService.getMeetingReport(meetingId);
            assertNotNull(report);
            assertNotNull(report.getTotalCollected());
            assertNotNull(report.getMemberCollections());
        }

        var allReports = reportService.getAllMeetingReports();
        assertNotNull(allReports);
    }

    @Test
    public void testCompletedMemberPaymentPreventionAndUpdate() {
        var scheduleReq = new com.redhun.aiswarya_ledger_api.dto.request.ScheduleMeetingRequest();
        scheduleReq.setMeetingDate(java.time.LocalDate.now().plusDays(15));
        var meeting = meetingService.scheduleMeeting(scheduleReq, adminUser);
        meeting = meetingService.openMeeting(meeting.getId());

        var memberList = memberRepository.findAll();
        if (!memberList.isEmpty()) {
            var member = memberList.get(0);
            var meetingEntity = meetingRepository.findById(meeting.getId()).orElseThrow();
            interestCalculationRepository.save(com.redhun.aiswarya_ledger_api.domain.entity.InterestCalculation.builder()
                    .member(member)
                    .interestPeriod(meeting.getInterestPeriod())
                    .meeting(meetingEntity)
                    .loanBalanceUsed(BigDecimal.ZERO)
                    .interestRate(new BigDecimal("0.0100"))
                    .interestAmount(BigDecimal.ZERO)
                    .status(com.redhun.aiswarya_ledger_api.domain.enums.InterestStatus.CALCULATED)
                    .calculatedBy(adminUser)
                    .build());
            final Long targetMeetingId = meeting.getId();
            final Long targetMemberId = member.getId();

            var request = new ProcessMemberRequest();
            request.setDepositAddition(new BigDecimal("500.00"));
            request.setNotes("First deposit");

            // 1. Process member
            memberProcessingService.processMember(targetMeetingId, targetMemberId, request, adminUser);

            // 2. Duplicate submission without isUpdate flag should fail
            var duplicateRequest = new ProcessMemberRequest();
            duplicateRequest.setDepositAddition(new BigDecimal("500.00"));
            duplicateRequest.setIsUpdate(false);

            assertThrows(BusinessException.class, () -> {
                memberProcessingService.processMember(targetMeetingId, targetMemberId, duplicateRequest, adminUser);
            });

            // 3. Submission with isUpdate = true should succeed cleanly
            var updateRequest = new ProcessMemberRequest();
            updateRequest.setDepositAddition(new BigDecimal("700.00"));
            updateRequest.setIsUpdate(true);
            updateRequest.setNotes("Updated deposit");

            var result = memberProcessingService.processMember(targetMeetingId, targetMemberId, updateRequest, adminUser);
            assertNotNull(result);
            assertEquals(com.redhun.aiswarya_ledger_api.domain.enums.MemberProcessingStatus.COMPLETED, result.getProcessingStatus());
            assertEquals(new BigDecimal("700.00"), result.getLastDepositAddition());
        }
    }

    @Test
    public void testMonthlyLedgerReportGeneration() {
        var report = reportService.getMonthlyLedgerReport(java.time.LocalDate.now().toString().substring(0, 7));
        assertNotNull(report);
        assertNotNull(report.getMemberRows());
        assertNotNull(report.getMeetingTotals());
    }

    @Test
    public void testMemberPersonalReportGeneration() {
        var memberList = memberRepository.findAll();
        if (!memberList.isEmpty()) {
            var member = memberList.get(0);
            var report = reportService.getMemberPersonalReport(member.getId(), java.time.LocalDate.now().toString().substring(0, 7));
            assertNotNull(report);
            assertEquals(member.getMemberNumber(), report.getMemberNumber());
            assertNotNull(report.getMeetingPayments());
        }
    }
}
