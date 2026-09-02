package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.CreateMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.request.UpdateMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.FinancialTransactionDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberAccountDto;
import com.redhun.aiswarya_ledger_api.dto.response.MemberDto;
import com.redhun.aiswarya_ledger_api.service.LedgerService;
import com.redhun.aiswarya_ledger_api.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final LedgerService ledgerService;
    private final com.redhun.aiswarya_ledger_api.service.ReportService reportService;
    private final com.redhun.aiswarya_ledger_api.repository.SpecialLoanTypeRepository specialLoanTypeRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberDto>> createMember(@Valid @RequestBody CreateMemberRequest request) {
        MemberDto member = memberService.createMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(member, "Member created successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.memberId")
    public ResponseEntity<ApiResponse<MemberDto>> getMemberById(@PathVariable Long id) {
        MemberDto member = memberService.getMemberById(id);
        return ResponseEntity.ok(ApiResponse.ok(member));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<MemberDto>>> getAllMembers(
            @RequestParam(required = false) String query,
            Pageable pageable) {
        Page<MemberDto> members = memberService.getAllMembers(query, pageable);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberDto>> updateMember(@PathVariable Long id, @RequestBody UpdateMemberRequest request) {
        MemberDto updated = memberService.updateMember(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Member updated successfully"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberDto>> getMyMemberProfile(@org.springframework.security.core.annotation.AuthenticationPrincipal com.redhun.aiswarya_ledger_api.security.UserPrincipal principal) {
        if (principal.getMemberId() == null) {
            throw new com.redhun.aiswarya_ledger_api.exception.BusinessException("MEMBER_NOT_LINKED", "Current user account is not linked to a member profile");
        }
        MemberDto member = memberService.getMemberById(principal.getMemberId());
        return ResponseEntity.ok(ApiResponse.ok(member));
    }

    @GetMapping({"/me/transactions", "/me/transaction"})
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<FinancialTransactionDto>>> getMyTransactions(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.redhun.aiswarya_ledger_api.security.UserPrincipal principal,
            Pageable pageable
    ) {
        if (principal.getMemberId() == null) {
            throw new com.redhun.aiswarya_ledger_api.exception.BusinessException("MEMBER_NOT_LINKED", "Current user account is not linked to a member profile");
        }
        Page<FinancialTransactionDto> transactions = ledgerService.getMemberTransactions(principal.getMemberId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }

    @GetMapping("/me/report")
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto>> getMyReport(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.redhun.aiswarya_ledger_api.security.UserPrincipal principal,
            @RequestParam(required = false) String yearMonth
    ) {
        if (principal.getMemberId() == null) {
            throw new com.redhun.aiswarya_ledger_api.exception.BusinessException("MEMBER_NOT_LINKED", "Current user account is not linked to a member profile");
        }
        com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto report = reportService.getMemberPersonalReport(principal.getMemberId(), yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.memberId")
    public ResponseEntity<ApiResponse<com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto>> getMemberPersonalReport(
            @PathVariable Long id,
            @RequestParam(required = false) String yearMonth
    ) {
        com.redhun.aiswarya_ledger_api.dto.response.MemberPersonalReportDto report = reportService.getMemberPersonalReport(id, yearMonth);
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/{id}/accounts")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.memberId")
    public ResponseEntity<ApiResponse<List<MemberAccountDto>>> getMemberAccounts(@PathVariable Long id) {
        List<MemberAccountDto> accounts = memberService.getMemberAccounts(id);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping({"/{id}/transactions", "/{id}/transaction"})
    @PreAuthorize("hasRole('ADMIN') or #id == principal.memberId")
    public ResponseEntity<ApiResponse<Page<FinancialTransactionDto>>> getMemberTransactions(
            @PathVariable Long id,
            Pageable pageable
    ) {
        Page<FinancialTransactionDto> transactions = ledgerService.getMemberTransactions(id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }

    @PostMapping("/{id}/issue-special-loan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialTransactionDto>> issueSpecialLoan(
            @PathVariable Long id,
            @Valid @RequestBody com.redhun.aiswarya_ledger_api.dto.request.IssueSpecialLoanRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.redhun.aiswarya_ledger_api.security.UserPrincipal principal
    ) {
        com.redhun.aiswarya_ledger_api.domain.entity.User adminUser = com.redhun.aiswarya_ledger_api.domain.entity.User.builder().id(principal.getId()).username(principal.getUsername()).build();
        com.redhun.aiswarya_ledger_api.domain.entity.SpecialLoanType slType = specialLoanTypeRepository.findById(request.getSpecialLoanTypeId())
                .orElseThrow(() -> new com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException("SpecialLoanType", "id", request.getSpecialLoanTypeId()));

        com.redhun.aiswarya_ledger_api.domain.entity.FinancialTransaction tx = ledgerService.recordTransaction(
                id,
                com.redhun.aiswarya_ledger_api.domain.enums.AccountType.SPECIAL_LOAN,
                com.redhun.aiswarya_ledger_api.domain.enums.TransactionType.LOAN_ISSUED,
                request.getAmount(),
                null,
                "SPECIAL_LOAN_ISSUE",
                null,
                request.getNotes(),
                null,
                request.getTransactionDate(),
                slType,
                adminUser
        );

        return ResponseEntity.ok(ApiResponse.ok(ledgerService.mapToDto(tx), "Special loan issued successfully"));
    }
}
