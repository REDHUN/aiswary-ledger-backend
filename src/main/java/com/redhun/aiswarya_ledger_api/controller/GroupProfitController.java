package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.CreateGroupProfitRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.GroupProfitDto;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.GroupProfitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/group-profits")
@RequiredArgsConstructor
public class GroupProfitController {

    private final GroupProfitService groupProfitService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<GroupProfitDto>>> getAllGroupProfits() {
        List<GroupProfitDto> list = groupProfitService.getAllGroupProfits();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GroupProfitDto>> createGroupProfit(
            @Valid @RequestBody CreateGroupProfitRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long userId = userPrincipal != null ? userPrincipal.getId() : null;
        GroupProfitDto result = groupProfitService.createGroupProfit(request, userId);
        return ResponseEntity.ok(ApiResponse.ok(result, "Group profit recorded successfully"));
    }
}
