package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.SaveFcmTokenRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.FcmTokenDto;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.FcmTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fcm-tokens")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FcmTokenDto>> registerFcmToken(
            @Valid @RequestBody SaveFcmTokenRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        FcmTokenDto dto = fcmTokenService.saveOrUpdateToken(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(dto, "FCM token registered successfully"));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unregisterFcmToken(
            @RequestParam String fcmToken,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        fcmTokenService.deleteToken(currentUser.getId(), fcmToken);
        return ResponseEntity.ok(ApiResponse.ok(null, "FCM token unregistered successfully"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<FcmTokenDto>>> getMyTokens(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<FcmTokenDto> tokens = fcmTokenService.getTokensByUserId(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(tokens));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<FcmTokenDto>>> getAllTokens() {
        List<FcmTokenDto> tokens = fcmTokenService.getAllTokens();
        return ResponseEntity.ok(ApiResponse.ok(tokens));
    }

    @DeleteMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> clearAllTokens() {
        fcmTokenService.clearAllTokens();
        return ResponseEntity.ok(ApiResponse.ok(null, "All FCM tokens cleared successfully"));
    }
}
