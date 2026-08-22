package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.CalculateInterestRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.InterestCalculationDto;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.InterestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members/{memberId}/interest")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;
    private final UserRepository userRepository;

    @PostMapping("/calculate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InterestCalculationDto>> calculateInterest(
            @PathVariable Long memberId,
            @Valid @RequestBody CalculateInterestRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User operator = userRepository.getReferenceById(userPrincipal.getId());
        InterestCalculationDto result = interestService.calculateInterest(
                memberId,
                request.getInterestPeriod(),
                request.getMeetingId(),
                operator
        );
        return ResponseEntity.ok(ApiResponse.ok(result, "Monthly interest calculated successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or #memberId == principal.memberId")
    public ResponseEntity<ApiResponse<List<InterestCalculationDto>>> getInterestHistory(
            @PathVariable Long memberId
    ) {
        List<InterestCalculationDto> history = interestService.getMemberInterestHistory(memberId);
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
