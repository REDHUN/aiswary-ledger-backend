package com.redhun.aiswarya_ledger_api.dto.response;

import com.redhun.aiswarya_ledger_api.domain.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthResponse {

    private String accessToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private UserRole role;
    private Long memberId;
}
