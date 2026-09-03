package com.redhun.aiswarya_ledger_api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTestNotificationRequest {

    private String title;
    private String body;
    private String fcmToken;
    private Long userId;
    private Boolean broadcast;
    private Map<String, String> data;
}
