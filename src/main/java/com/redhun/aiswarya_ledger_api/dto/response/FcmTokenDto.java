package com.redhun.aiswarya_ledger_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenDto {

    private Long id;
    private Long userId;
    private String fcmToken;
    private String deviceType;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
