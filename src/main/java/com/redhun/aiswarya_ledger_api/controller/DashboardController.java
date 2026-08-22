package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.DashboardSummaryDto;
import com.redhun.aiswarya_ledger_api.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getDashboardSummary() {
        DashboardSummaryDto summary = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
