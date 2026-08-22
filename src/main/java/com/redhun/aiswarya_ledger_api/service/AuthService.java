package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.dto.request.LoginRequest;
import com.redhun.aiswarya_ledger_api.dto.response.JwtAuthResponse;
import com.redhun.aiswarya_ledger_api.security.JwtTokenProvider;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public JwtAuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        return JwtAuthResponse.builder()
                .accessToken(jwt)
                .tokenType("Bearer")
                .userId(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .role(userPrincipal.getRole())
                .memberId(userPrincipal.getMemberId())
                .build();
    }
}
