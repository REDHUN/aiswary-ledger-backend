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
    public ResponseEntity<ApiResponse<Page<MemberDto>>> getAllMembers(Pageable pageable) {
        Page<MemberDto> members = memberService.getAllMembers(pageable);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberDto>> updateMember(@PathVariable Long id, @RequestBody UpdateMemberRequest request) {
        MemberDto updated = memberService.updateMember(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Member updated successfully"));
    }

    @GetMapping("/{id}/accounts")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.memberId")
    public ResponseEntity<ApiResponse<List<MemberAccountDto>>> getMemberAccounts(@PathVariable Long id) {
        List<MemberAccountDto> accounts = memberService.getMemberAccounts(id);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.memberId")
    public ResponseEntity<ApiResponse<Page<FinancialTransactionDto>>> getMemberTransactions(
            @PathVariable Long id,
            Pageable pageable
    ) {
        Page<FinancialTransactionDto> transactions = ledgerService.getMemberTransactions(id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}
