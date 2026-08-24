package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.ProcessMemberRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.MemberProcessingFormDto;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.MemberProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/members/{memberId}")
@RequiredArgsConstructor
public class MemberProcessingController {

    private final MemberProcessingService memberProcessingService;
    private final UserRepository userRepository;

    @GetMapping("/processing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberProcessingFormDto>> getMemberProcessingForm(
            @PathVariable Long meetingId,
            @PathVariable Long memberId
    ) {
        MemberProcessingFormDto form = memberProcessingService.getMemberProcessingForm(meetingId, memberId);
        return ResponseEntity.ok(ApiResponse.ok(form));
    }

    @PostMapping("/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberProcessingFormDto>> processMember(
            @PathVariable Long meetingId,
            @PathVariable Long memberId,
            @Valid @RequestBody ProcessMemberRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        User operator = userRepository.getReferenceById(userPrincipal.getId());
        MemberProcessingFormDto result = memberProcessingService.processMember(meetingId, memberId, request, operator);
        return ResponseEntity.ok(ApiResponse.ok(result, "Member processed successfully"));
    }

    @PutMapping("/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MemberProcessingFormDto>> updateMemberProcess(
            @PathVariable Long meetingId,
            @PathVariable Long memberId,
            @Valid @RequestBody ProcessMemberRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        request.setIsUpdate(true);
        User operator = userRepository.getReferenceById(userPrincipal.getId());
        MemberProcessingFormDto result = memberProcessingService.processMember(meetingId, memberId, request, operator);
        return ResponseEntity.ok(ApiResponse.ok(result, "Member processing updated successfully"));
    }
}
