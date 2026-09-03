package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.SendTestNotificationRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.NotificationTestResponse;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Send a test push notification.
     * If no body is provided, it targets the logged-in user's device(s).
     * If target token, user ID, or broadcast flag is provided, it routes accordingly.
     */
    @PostMapping("/test")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationTestResponse>> testNotification(
            @RequestBody(required = false) SendTestNotificationRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Long userId = (currentUser != null) ? currentUser.getId() : null;
        NotificationTestResponse result = notificationService.sendTestNotification(request, userId);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }

    /**
     * Send a test notification specifically to the current authenticated user's registered device(s).
     */
    @PostMapping("/test-my-device")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationTestResponse>> testMyDevice(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        SendTestNotificationRequest request = SendTestNotificationRequest.builder()
                .userId(currentUser.getId())
                .title("Aiswarya Ledger - Device Test")
                .body("Notification test delivered successfully to your device!")
                .build();

        NotificationTestResponse result = notificationService.sendTestNotification(request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }

    /**
     * Broadcast a notification to all active devices in the database (Admin only).
     */
    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationTestResponse>> broadcastNotification(
            @RequestBody SendTestNotificationRequest request
    ) {
        if (request == null) {
            request = new SendTestNotificationRequest();
        }
        request.setBroadcast(true);

        NotificationTestResponse result = notificationService.sendTestNotification(request, null);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }
}
