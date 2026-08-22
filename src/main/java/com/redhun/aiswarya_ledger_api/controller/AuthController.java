package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.LoginRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.JwtAuthResponse;
import com.redhun.aiswarya_ledger_api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        JwtAuthResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.ok(response, "User logged in successfully"));
    }
}
