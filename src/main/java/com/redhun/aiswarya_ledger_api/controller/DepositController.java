package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.CreateDepositRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.FinancialTransactionDto;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members/{memberId}/deposits")
@RequiredArgsConstructor
public class DepositController {

    private final LedgerService ledgerService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialTransactionDto>> addDeposit(
            @PathVariable Long memberId,
            @Valid @RequestBody CreateDepositRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User operator = userRepository.getReferenceById(userPrincipal.getId());
        FinancialTransactionDto tx = ledgerService.addDeposit(
                memberId,
                request.getAmount(),
                request.getMeetingId(),
                request.getDescription(),
                request.getTransactionDate(),
                operator
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(tx, "Deposit recorded successfully"));
    }
}
