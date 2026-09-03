package com.redhun.aiswarya_ledger_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveFcmTokenRequest {

    @NotBlank(message = "FCM token is required")
    @Size(max = 512, message = "FCM token must not exceed 512 characters")
    private String fcmToken;

    @Size(max = 50, message = "Device type must not exceed 50 characters")
    private String deviceType;
}
