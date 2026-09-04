package com.redhun.aiswarya_ledger_api.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.redhun.aiswarya_ledger_api.domain.entity.FcmToken;
import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.domain.enums.NotificationType;
import com.redhun.aiswarya_ledger_api.dto.request.CreateNotificationRequest;
import com.redhun.aiswarya_ledger_api.dto.request.SendTestNotificationRequest;
import com.redhun.aiswarya_ledger_api.dto.response.NotificationDto;
import com.redhun.aiswarya_ledger_api.dto.response.NotificationTestResponse;
import com.redhun.aiswarya_ledger_api.dto.response.UnreadNotificationCountDto;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.FcmTokenRepository;
import com.redhun.aiswarya_ledger_api.repository.NotificationRepository;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /**
     * Firebase allows a maximum of 500 registration tokens
     * in one multicast request.
     */
    private static final int FCM_MAX_TOKENS_PER_REQUEST = 500;

    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;


    // ============================================================
    // CREATE, PERSIST & SEND NOTIFICATION (PIPELINE)
    // ============================================================

    /**
     * Creates notification records in database and sends FCM push notification.
     */
    @Transactional
    public List<NotificationDto> createAndSendNotification(CreateNotificationRequest request) {
        log.info("Creating notification: title='{}', type={}, userId={}, broadcast={}",
                request.getTitle(), request.getNotificationType(), request.getUserId(), request.getBroadcast());

        // 1. Resolve target users
        List<User> targetUsers = resolveTargetUsers(request);
        if (targetUsers.isEmpty()) {
            log.warn("No target users found for notification creation.");
            return Collections.emptyList();
        }

        // 2. Persist in database for all target users
        List<com.redhun.aiswarya_ledger_api.domain.entity.Notification> notifications = new ArrayList<>();
        for (User user : targetUsers) {
            com.redhun.aiswarya_ledger_api.domain.entity.Notification entity =
                    com.redhun.aiswarya_ledger_api.domain.entity.Notification.builder()
                            .user(user)
                            .title(request.getTitle())
                            .body(request.getBody())
                            .notificationType(request.getNotificationType())
                            .referenceId(request.getReferenceId())
                            .data(request.getData() != null ? new HashMap<>(request.getData()) : new HashMap<>())
                            .isRead(false)
                            .build();
            notifications.add(entity);
        }

        List<com.redhun.aiswarya_ledger_api.domain.entity.Notification> savedEntities =
                notificationRepository.saveAll(notifications);
        log.info("Persisted {} notification record(s) in database", savedEntities.size());

        // 3. Collect target FCM tokens
        List<String> targetTokens = new ArrayList<>();
        for (User user : targetUsers) {
            List<FcmToken> userTokens = fcmTokenRepository.findByUserId(user.getId());
            targetTokens.addAll(
                    userTokens.stream()
                            .map(FcmToken::getFcmToken)
                            .filter(Objects::nonNull)
                            .toList()
            );
        }

        List<String> cleanedTokens = cleanTokens(targetTokens);

        // 4. Prepare FCM Data payload
        Map<String, String> fcmData = new HashMap<>();
        if (request.getData() != null) {
            fcmData.putAll(request.getData());
        }
        fcmData.put("notificationType", request.getNotificationType().name());
        fcmData.put("type", request.getNotificationType().name());
        if (request.getReferenceId() != null) {
            fcmData.put("referenceId", String.valueOf(request.getReferenceId()));
        }
        if (savedEntities.size() == 1) {
            fcmData.put("notificationId", String.valueOf(savedEntities.getFirst().getId()));
        }

        // 5. Send FCM Multicast
        if (!cleanedTokens.isEmpty()) {
            sendMulticastNotification(
                    cleanedTokens,
                    request.getTitle(),
                    request.getBody(),
                    fcmData
            );
        } else {
            log.info("No FCM tokens registered for target users. FCM push skipped.");
        }

        return savedEntities.stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    private List<User> resolveTargetUsers(CreateNotificationRequest request) {
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));
            return List.of(user);
        } else if (Boolean.TRUE.equals(request.getBroadcast())) {
            return userRepository.findByIsActiveTrue();
        } else {
            return userRepository.findByIsActiveTrue();
        }
    }


    // ============================================================
    // USER NOTIFICATION INBOX QUERIES
    // ============================================================

    @Transactional(readOnly = true)
    public Page<NotificationDto> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountDto getUnreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return new UnreadNotificationCountDto(count);
    }

    @Transactional
    public NotificationDto markAsRead(Long notificationId, Long userId) {
        com.redhun.aiswarya_ledger_api.domain.entity.Notification notification =
                notificationRepository.findByIdAndUserId(notificationId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification = notificationRepository.save(notification);
        }

        return NotificationDto.fromEntity(notification);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        int updatedRows = notificationRepository.markAllAsReadByUserId(userId, ZonedDateTime.now());
        log.info("Marked all notifications as read for user {}: {} rows updated", userId, updatedRows);
        return updatedRows;
    }


    // ============================================================
    // MEETING COMPLETED NOTIFICATION
    // ============================================================

    /**
     * Sends meeting completed notification to all registered
     * user devices and persists the notification in the database.
     */
    @Async
    @Transactional
    public void sendMeetingCompletedNotification(Meeting meeting) {

        if (meeting == null) {
            log.warn("Cannot send meeting completed notification: meeting is null");
            return;
        }

        String title = "യോഗം പൂർത്തിയായി (Meeting Completed)";

        String body = String.format(
                "മീറ്റിംഗ് #%d (%s) പൂർത്തിയായി. " +
                        "Meeting #%d has been completed.",
                meeting.getMeetingNumber(),
                meeting.getMeetingDate() != null
                        ? meeting.getMeetingDate().toString()
                        : "",
                meeting.getMeetingNumber()
        );

        Map<String, String> data = new HashMap<>();
        data.put("type", NotificationType.MEETING.name());
        data.put("notificationType", NotificationType.MEETING.name());
        data.put("meetingId", String.valueOf(meeting.getId()));
        data.put("referenceId", String.valueOf(meeting.getId()));
        data.put(
                "meetingNumber",
                String.valueOf(meeting.getMeetingNumber())
        );

        if (meeting.getMeetingDate() != null) {
            data.put(
                    "meetingDate",
                    meeting.getMeetingDate().toString()
            );
        }

        if (meeting.getStatus() != null) {
            data.put(
                    "status",
                    meeting.getStatus().name()
            );
        }

        // Save DB notifications for all active users
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        if (!activeUsers.isEmpty()) {
            List<com.redhun.aiswarya_ledger_api.domain.entity.Notification> dbNotifications = activeUsers.stream()
                    .map(u -> com.redhun.aiswarya_ledger_api.domain.entity.Notification.builder()
                            .user(u)
                            .title(title)
                            .body(body)
                            .notificationType(NotificationType.MEETING)
                            .referenceId(meeting.getId())
                            .data(new HashMap<>(data))
                            .isRead(false)
                            .build())
                    .toList();
            notificationRepository.saveAll(dbNotifications);
            log.info("Persisted meeting completed notifications for {} active user(s)", activeUsers.size());
        }

        if (firebaseMessaging == null) {
            log.warn(
                    "FirebaseMessaging is not initialized. " +
                            "Meeting completed push notification was not sent."
            );
            return;
        }

        List<String> tokens = getAllTokens();

        if (tokens.isEmpty()) {
            log.info(
                    "No FCM tokens found in database. " +
                            "Skipping push notification for Meeting #{}",
                    meeting.getMeetingNumber()
            );
            return;
        }

        log.info(
                "Sending meeting completed push notification for Meeting #{} " +
                        "to {} device(s)",
                meeting.getMeetingNumber(),
                tokens.size()
        );

        sendMulticastNotification(
                tokens,
                title,
                body,
                data
        );
    }


    // ============================================================
    // TEST NOTIFICATION
    // ============================================================

    /**
     * Sends a test notification.
     *
     * Target priority:
     *
     * 1. Explicit FCM token
     * 2. User ID
     * 3. Broadcast
     * 4. fallbackUserId
     * 5. All tokens
     */
    public NotificationTestResponse sendTestNotification(
            SendTestNotificationRequest request,
            Long fallbackUserId
    ) {

        if (firebaseMessaging == null) {

            log.warn(
                    "Cannot send test notification: " +
                            "FirebaseMessaging is not initialized."
            );

            return NotificationTestResponse.builder()
                    .totalTargeted(0)
                    .successCount(0)
                    .failureCount(0)
                    .status("FIREBASE_NOT_CONFIGURED")
                    .message(
                            "Firebase messaging is not " +
                                    "configured/initialized on the server."
                    )
                    .build();
        }

        // --------------------------------------------------------
        // Resolve target tokens
        // --------------------------------------------------------

        List<String> targetTokens = resolveTargetTokens(
                request,
                fallbackUserId
        );

        if (targetTokens.isEmpty()) {

            return NotificationTestResponse.builder()
                    .totalTargeted(0)
                    .successCount(0)
                    .failureCount(0)
                    .status("NO_TOKENS_FOUND")
                    .message(
                            "No FCM tokens found for the specified target."
                    )
                    .build();
        }

        // --------------------------------------------------------
        // Notification title
        // --------------------------------------------------------

        String title =
                request != null
                        && request.getTitle() != null
                        && !request.getTitle().trim().isEmpty()
                        ? request.getTitle().trim()
                        : "Aiswarya Ledger - Test Notification";

        // --------------------------------------------------------
        // Notification body
        // --------------------------------------------------------

        String formattedTime =
                ZonedDateTime.now()
                        .format(
                                DateTimeFormatter.ofPattern(
                                        "hh:mm a, dd MMM yyyy"
                                    )
                        );

        String body =
                request != null
                        && request.getBody() != null
                        && !request.getBody().trim().isEmpty()
                        ? request.getBody().trim()
                        : "This is a test notification from " +
                        "Aiswarya Ledger sent at " +
                        formattedTime;

        // --------------------------------------------------------
        // Notification data
        // --------------------------------------------------------

        Map<String, String> data = new HashMap<>();

        data.put(
                "type",
                "TEST_NOTIFICATION"
        );

        data.put(
                "sentAt",
                ZonedDateTime.now().toString()
        );

        if (request != null && request.getData() != null) {
            data.putAll(request.getData());
        }

        log.info(
                "Sending test FCM notification to {} token(s). " +
                        "Title='{}'",
                targetTokens.size(),
                title
        );

        // --------------------------------------------------------
        // Send
        // --------------------------------------------------------

        NotificationSendResult result =
                sendMulticastNotificationWithResult(
                        targetTokens,
                        title,
                        body,
                        data
                );

        String status;

        if (result.successCount() > 0
                && result.failureCount() == 0) {

            status = "SUCCESS";

        } else if (result.successCount() > 0) {

            status = "PARTIAL_SUCCESS";

        } else if (result.failureCount() > 0) {

            status = "FAILED";

        } else {

            status = "COMPLETED";
        }

        String message;

        if (result.successCount() > 0
                && result.failureCount() > 0) {

            message = String.format(
                    "Notification delivered to %d device(s). " +
                            "%d token(s) failed. Invalid/unregistered tokens " +
                            "were cleaned up.",
                    result.successCount(),
                    result.failureCount()
            );

        } else if (result.successCount() > 0) {

            message = String.format(
                    "Notification delivered successfully to %d device(s).",
                    result.successCount()
            );

        } else {

            message = String.format(
                    "Delivery failed for %d target token(s).",
                    result.failureCount()
            );
        }

        return NotificationTestResponse.builder()
                .totalTargeted(targetTokens.size())
                .successCount(result.successCount())
                .failureCount(result.failureCount())
                .status(status)
                .message(message)
                .errors(
                        result.errors().isEmpty()
                                ? null
                                : result.errors()
                )
                .build();
    }


    // ============================================================
    // RESOLVE TARGET TOKENS
    // ============================================================

    private List<String> resolveTargetTokens(
            SendTestNotificationRequest request,
            Long fallbackUserId
    ) {

        List<String> targetTokens = new ArrayList<>();

        if (request != null
                && request.getFcmToken() != null
                && !request.getFcmToken().trim().isEmpty()) {

            targetTokens.add(
                    request.getFcmToken().trim()
            );

        } else if (request != null
                && request.getUserId() != null) {

            List<FcmToken> userTokens =
                    fcmTokenRepository.findByUserId(
                            request.getUserId()
                    );

            targetTokens.addAll(
                    userTokens.stream()
                            .map(FcmToken::getFcmToken)
                            .filter(Objects::nonNull)
                            .toList()
            );

        } else if (request != null
                && Boolean.TRUE.equals(request.getBroadcast())) {

            targetTokens.addAll(
                    getAllTokens()
            );

        } else if (fallbackUserId != null) {

            List<FcmToken> userTokens =
                    fcmTokenRepository.findByUserId(
                            fallbackUserId
                    );

            targetTokens.addAll(
                    userTokens.stream()
                            .map(FcmToken::getFcmToken)
                            .filter(Objects::nonNull)
                            .toList()
            );

        } else {

            targetTokens.addAll(
                    getAllTokens()
            );
        }

        return cleanTokens(targetTokens);
    }


    // ============================================================
    // GET ALL TOKENS
    // ============================================================

    private List<String> getAllTokens() {

        List<FcmToken> allTokens =
                fcmTokenRepository.findAll();

        return cleanTokens(
                allTokens.stream()
                        .map(FcmToken::getFcmToken)
                        .filter(Objects::nonNull)
                        .toList()
        );
    }


    // ============================================================
    // CLEAN TOKEN LIST
    // ============================================================

    private List<String> cleanTokens(List<String> tokens) {

        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }

        return tokens.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }


    // ============================================================
    // MULTICAST NOTIFICATION
    // ============================================================

    /**
     * Sends a notification to multiple FCM tokens.
     *
     * Firebase supports a maximum of 500 tokens per request,
     * therefore this method automatically splits larger lists.
     */
    public void sendMulticastNotification(
            List<String> tokens,
            String title,
            String body,
            Map<String, String> data
    ) {

        NotificationSendResult result =
                sendMulticastNotificationWithResult(
                        tokens,
                        title,
                        body,
                        data
                );

        log.info(
                "FCM notification finished. " +
                        "Success: {}, Failed: {}, Total: {}",
                result.successCount(),
                result.failureCount(),
                result.totalTargeted()
        );
    }


    // ============================================================
    // MULTICAST WITH RESULT
    // ============================================================

    private NotificationSendResult sendMulticastNotificationWithResult(
            List<String> tokens,
            String title,
            String body,
            Map<String, String> data
    ) {

        if (firebaseMessaging == null) {

            log.warn(
                    "FirebaseMessaging is not initialized. " +
                            "Notification '{}' was not sent.",
                    title
            );

            return new NotificationSendResult(
                    0,
                    0,
                    0,
                    Collections.emptyList()
            );
        }

        List<String> distinctTokens =
                cleanTokens(tokens);

        if (distinctTokens.isEmpty()) {

            log.info(
                    "No valid FCM tokens available."
            );

            return new NotificationSendResult(
                    0,
                    0,
                    0,
                    Collections.emptyList()
            );
        }

        int totalSuccess = 0;
        int totalFailure = 0;

        List<String> errors = new ArrayList<>();
        List<String> staleTokens = new ArrayList<>();

        // --------------------------------------------------------
        // Split into batches of maximum 500 tokens
        // --------------------------------------------------------

        for (
                int start = 0;
                start < distinctTokens.size();
                start += FCM_MAX_TOKENS_PER_REQUEST
        ) {

            int end = Math.min(
                    start + FCM_MAX_TOKENS_PER_REQUEST,
                    distinctTokens.size()
            );

            List<String> batchTokens =
                    distinctTokens.subList(start, end);

            log.info(
                    "Sending FCM batch. Tokens {} - {} of {}",
                    start + 1,
                    end,
                    distinctTokens.size()
            );

            try {

                MulticastMessage message =
                        buildMulticastMessage(
                                batchTokens,
                                title,
                                body,
                                data
                        );

                BatchResponse response =
                        firebaseMessaging.sendEachForMulticast(
                                message
                        );

                totalSuccess +=
                        response.getSuccessCount();

                totalFailure +=
                        response.getFailureCount();

                // ------------------------------------------------
                // Process individual responses
                // ------------------------------------------------

                List<SendResponse> responses =
                        response.getResponses();

                for (int i = 0; i < responses.size(); i++) {

                    SendResponse sendResponse =
                            responses.get(i);

                    String token =
                            batchTokens.get(i);

                    if (sendResponse.isSuccessful()) {

                        log.debug(
                                "FCM notification sent successfully. " +
                                        "Token={}, MessageId={}",
                                preview(token),
                                sendResponse.getMessageId()
                        );

                        continue;
                    }

                    // ------------------------------------------------
                    // Failed response
                    // ------------------------------------------------

                    FirebaseMessagingException exception =
                            sendResponse.getException();

                    MessagingErrorCode errorCode =
                            exception != null
                                    ? exception.getMessagingErrorCode()
                                    : null;

                    String errorMessage =
                            exception != null
                                    ? exception.getMessage()
                                    : "Unknown FCM error";

                    log.warn(
                            "FCM notification failed. " +
                                    "Token={}, ErrorCode={}, Message={}",
                            preview(token),
                            errorCode,
                            errorMessage
                    );

                    errors.add(
                            "Token "
                                    + preview(token)
                                    + " failed: "
                                    + errorCode
                                    + " - "
                                    + errorMessage
                    );

                    // ------------------------------------------------
                    // IMPORTANT:
                    // Only remove confirmed UNREGISTERED tokens.
                    // ------------------------------------------------

                    if (
                            errorCode ==
                                    MessagingErrorCode.UNREGISTERED
                    ) {

                        staleTokens.add(token);
                    }
                }

            } catch (FirebaseMessagingException ex) {

                /*
                 * This means the entire multicast request failed,
                 * rather than necessarily meaning individual tokens
                 * are invalid.
                 */

                log.error(
                        "FCM multicast request failed. " +
                                "ErrorCode={}, Message={}",
                        ex.getMessagingErrorCode(),
                        ex.getMessage(),
                        ex
                );

                errors.add(
                        "FCM multicast request failed: "
                                + ex.getMessagingErrorCode()
                                + " - "
                                + ex.getMessage()
                );

                totalFailure += batchTokens.size();

            } catch (Exception ex) {

                /*
                 * Do NOT delete tokens here.
                 *
                 * An unexpected exception does not prove that
                 * a token is stale.
                 */

                log.error(
                        "Unexpected error while sending FCM batch. " +
                                "BatchSize={}, Error={}",
                        batchTokens.size(),
                        ex.getMessage(),
                        ex
                );

                errors.add(
                        "Unexpected FCM error for batch of "
                                + batchTokens.size()
                                + " token(s): "
                                + ex.getMessage()
                );

                totalFailure += batchTokens.size();
            }
        }

        // --------------------------------------------------------
        // Remove confirmed stale tokens
        // --------------------------------------------------------

        if (!staleTokens.isEmpty()) {

            /*
             * Remove duplicates before database cleanup.
             */

            List<String> uniqueStaleTokens =
                    staleTokens.stream()
                            .distinct()
                            .toList();

            log.info(
                    "Cleaning up {} unregistered FCM token(s).",
                    uniqueStaleTokens.size()
            );

            cleanUpStaleTokens(
                    uniqueStaleTokens
            );
        }

        return new NotificationSendResult(
                distinctTokens.size(),
                totalSuccess,
                totalFailure,
                errors
        );
    }


    // ============================================================
    // BUILD MULTICAST MESSAGE
    // ============================================================

    private MulticastMessage buildMulticastMessage(
            List<String> tokens,
            String title,
            String body,
            Map<String, String> data
    ) {

        Notification notification =
                Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build();

        AndroidNotification androidNotification =
                AndroidNotification.builder()
                        .setSound("default")
                        .setChannelId(
                                "meeting_notifications"
                        )
                        .build();

        AndroidConfig androidConfig =
                AndroidConfig.builder()
                        .setPriority(
                                AndroidConfig.Priority.HIGH
                        )
                        .setNotification(
                                androidNotification
                        )
                        .build();

        ApnsConfig apnsConfig =
                ApnsConfig.builder()
                        .setAps(
                                Aps.builder()
                                        .setSound("default")
                                        .build()
                        )
                        .build();

        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification)
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
                .putAllData(
                        data != null
                                ? data
                                : Collections.emptyMap()
                )
                .build();
    }


    // ============================================================
    // CLEANUP STALE TOKENS
    // ============================================================

    @Transactional
    public void cleanUpStaleTokens(
            List<String> staleTokens
    ) {

        if (staleTokens == null
                || staleTokens.isEmpty()) {

            return;
        }

        log.info(
                "Cleaning up {} unregistered FCM token(s) " +
                        "from database.",
                staleTokens.size()
        );

        for (String token : staleTokens) {

            if (token == null
                    || token.trim().isEmpty()) {

                continue;
            }

            try {

                fcmTokenRepository.deleteByFcmToken(
                        token
                );

                log.info(
                        "Deleted stale FCM token {}",
                        preview(token)
                );

            } catch (Exception e) {

                log.warn(
                        "Failed to delete stale FCM token {}: {}",
                        preview(token),
                        e.getMessage()
                );
            }
        }
    }


    // ============================================================
    // TOKEN PREVIEW
    // ============================================================

    private String preview(String token) {

        if (token == null) {
            return "null";
        }

        return token.length() > 12
                ? token.substring(0, 12) + "..."
                : token;
    }


    // ============================================================
    // RESULT RECORD
    // ============================================================

    private record NotificationSendResult(
            int totalTargeted,
            int successCount,
            int failureCount,
            List<String> errors
    ) {
    }
}