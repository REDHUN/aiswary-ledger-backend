package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.FinancialReportDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberBalanceReportDto;
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

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialReportDto>> getFinancialSummary() {
        FinancialReportDto summary = reportService.getFinancialSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/period")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialReportDto>> getPeriodReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        FinancialReportDto periodReport = reportService.getPeriodReport(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(periodReport));
    }

    @GetMapping("/member-balances")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<MemberBalanceReportDto>>> getMemberBalancesReport() {
        List<MemberBalanceReportDto> memberBalances = reportService.getMemberBalancesReport();
        return ResponseEntity.ok(ApiResponse.ok(memberBalances));
    }

    @GetMapping("/meetings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto>>> getAllMeetingReports() {
        List<com.redhun.aiswarya_ledger_api.dto.response.MeetingReportDto> reports = reportService.getAllMeetingReports();
        return ResponseEntity.ok(ApiResponse.ok(reports));
    }

    @GetMapping("/meetings/{meetingId}")
    @PreAuthorize("hasRole('ADMIN')")
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
}
