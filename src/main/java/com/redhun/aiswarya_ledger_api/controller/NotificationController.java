package com.redhun.aiswarya_ledger_api.controller;

import com.redhun.aiswarya_ledger_api.dto.request.CreateNotificationRequest;
import com.redhun.aiswarya_ledger_api.dto.request.SendTestNotificationRequest;
import com.redhun.aiswarya_ledger_api.dto.response.ApiResponse;
import com.redhun.aiswarya_ledger_api.dto.response.NotificationDto;
import com.redhun.aiswarya_ledger_api.dto.response.NotificationTestResponse;
import com.redhun.aiswarya_ledger_api.dto.response.UnreadNotificationCountDto;
import com.redhun.aiswarya_ledger_api.security.UserPrincipal;
import com.redhun.aiswarya_ledger_api.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Create, persist in database, and dispatch push notification (Admin only).
     * Supports single-user target (via userId) or all active users (via broadcast=true or default).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> createNotification(
            @Valid @RequestBody CreateNotificationRequest request
    ) {
        List<NotificationDto> notifications = notificationService.createAndSendNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(notifications, "Notification(s) created and dispatched successfully"));
    }

    /**
     * Get paginated notifications for the current authenticated user.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotificationDto>>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDto> notifications = notificationService.getUserNotifications(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }

    /**
     * Get count of unread notifications for the current authenticated user.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UnreadNotificationCountDto>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        UnreadNotificationCountDto countDto = notificationService.getUnreadCount(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(countDto));
    }

    /**
     * Mark a specific notification as read.
     */
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        NotificationDto notification = notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(notification, "Notification marked as read"));
    }

    /**
     * Mark all unread notifications as read for current user.
     */
    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        int updatedCount = notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updatedCount", updatedCount), "All notifications marked as read"));
    }

    // ============================================================
    // DIAGNOSTIC / TEST ENDPOINTS
    // ============================================================

    /**
     * Send a test push notification.
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
