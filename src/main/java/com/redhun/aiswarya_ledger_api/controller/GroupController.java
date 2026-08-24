package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.CreateMemberGroupRequest;
import com.redhun.aiswarya_ledger_api.dto.request.IssueGroupLoanRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.GroupLoanSummaryDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberGroupDto;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.GroupLoanService;
import com.redhun.aiswarya_ledger_api.service.MemberGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final MemberGroupService memberGroupService;
    private final GroupLoanService groupLoanService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<MemberGroupDto>>> getAllGroups() {
        List<MemberGroupDto> groups = memberGroupService.getAllGroups();
        return ResponseEntity.ok(ApiResponse.ok(groups));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<MemberGroupDto>> getGroupById(@PathVariable Long id) {
        MemberGroupDto group = memberGroupService.getGroupById(id);
        return ResponseEntity.ok(ApiResponse.ok(group));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberGroupDto>> createGroup(@Valid @RequestBody CreateMemberGroupRequest request) {
        MemberGroupDto created = memberGroupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created, "Group created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberGroupDto>> updateGroup(@PathVariable Long id, @Valid @RequestBody CreateMemberGroupRequest request) {
        MemberGroupDto updated = memberGroupService.updateGroup(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Group updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long id) {
        memberGroupService.deleteGroup(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Group deleted successfully"));
    }

    @PostMapping("/issue-loan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GroupLoanSummaryDto>> issueGroupLoan(
            @Valid @RequestBody IssueGroupLoanRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        User operator = userRepository.getReferenceById(principal.getId());
        GroupLoanSummaryDto summary = groupLoanService.issueGroupLoan(request, operator);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(summary, "Group loan issued successfully"));
    }

    @GetMapping("/loans")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<GroupLoanSummaryDto>>> getAllGroupLoans() {
        List<GroupLoanSummaryDto> loans = groupLoanService.getAllGroupLoans();
        return ResponseEntity.ok(ApiResponse.ok(loans));
    }

    @GetMapping("/loans/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<GroupLoanSummaryDto>> getGroupLoanById(@PathVariable Long id) {
        GroupLoanSummaryDto loan = groupLoanService.getGroupLoanById(id);
        return ResponseEntity.ok(ApiResponse.ok(loan));
    }
}
