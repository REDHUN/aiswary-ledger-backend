package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.response.*;
import com.redhun.aiswarya_ledger_api.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;




    @GetMapping("/member-balances")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<MemberBalanceReportDto>>> getMemberBalancesReport() {
        List<MemberBalanceReportDto> memberBalances = reportService.getMemberBalancesReport();
        return ResponseEntity.ok(ApiResponse.ok(memberBalances));
    }

    @GetMapping("/meetings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto>>> getAllMeetingReports() {
        List<com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto> reports = reportService.getAllMeetingReports();
        return ResponseEntity.ok(ApiResponse.ok(reports));
    }

    @GetMapping("/meetings/{meetingId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto>> getMeetingReport(@PathVariable Long meetingId) {
        com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto report = reportService.getMeetingReport(meetingId);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/monthly-ledger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.redhun.aiswarya_ledger_api.dto.response.MonthlyLedgerReportDto>> getMonthlyLedgerReport(
            @RequestParam(required = false) String yearMonth
    ) {
        com.redhun.aiswarya_ledger_api.dto.response.MonthlyLedgerReportDto report = reportService.getMonthlyLedgerReport(yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/category")
    public ResponseEntity<ApiResponse<CategoryReportDto>> getCategoryReport() {
        CategoryReportDto report = reportService.getCategoryReport();
        return ResponseEntity.ok(ApiResponse.ok(report, "Category report fetched successfully"));
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto>> getMemberReport(
            @PathVariable Long memberId,
            @RequestParam(required = false) String yearMonth
    ) {
        com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto report = reportService.getMemberPersonalReport(memberId, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

}
