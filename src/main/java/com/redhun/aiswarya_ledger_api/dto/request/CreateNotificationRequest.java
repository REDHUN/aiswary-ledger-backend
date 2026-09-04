package com.redhun.aiswarya_ledger_api.dto.request;

import com.redhun.aiswarya_ledger_api.domain.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {

    /**
     * Target user ID. If null and broadcast is true, notification will be sent to all active users.
     */
    private Long userId;

    /**
     * Set to true to send notification to all active users.
     */
    private Boolean broadcast;

    @NotBlank(message = "Notification title is required")
    private String title;

    @NotBlank(message = "Notification body is required")
    private String body;

    @NotNull(message = "Notification type is required")
    private NotificationType notificationType;

    /**
     * Associated domain entity ID (e.g. meeting ID, loan ID, transaction ID).
     */
    private Long referenceId;

    /**
     * Custom key-value pairs for client-side deep linking and metadata.
     */
    private Map<String, String> data;
}
