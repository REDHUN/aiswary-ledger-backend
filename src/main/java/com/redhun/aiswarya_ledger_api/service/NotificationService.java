package com.redhun.aiswarya_ledger_api.service;

import com.google.firebase.messaging.*;
import com.redhun.aiswarya_ledger_api.domain.entity.FcmToken;
import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final FcmTokenRepository fcmTokenRepository;

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    /**
     * Send meeting completed notification to all user devices with registered FCM tokens.
     */
    @Async
    public void sendMeetingCompletedNotification(Meeting meeting) {
        if (meeting == null) {
            log.warn("Cannot send meeting completed notification: meeting is null");
            return;
        }

        List<FcmToken> fcmTokens = fcmTokenRepository.findAll();
        if (fcmTokens.isEmpty()) {
            log.info("No FCM tokens found in database. Skipping notification for Meeting #{}", meeting.getMeetingNumber());
            return;
        }

        List<String> tokenStrings = fcmTokens.stream()
                .map(FcmToken::getFcmToken)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (tokenStrings.isEmpty()) {
            log.info("No valid non-empty FCM tokens found. Skipping notification for Meeting #{}", meeting.getMeetingNumber());
            return;
        }

        String title = "യോഗം പൂർത്തിയായി (Meeting Completed)";
        String body = String.format("മീറ്റിംഗ് #%d (%s) പൂർത്തിയായി. Meeting #%d has been completed.",
                meeting.getMeetingNumber(),
                meeting.getMeetingDate() != null ? meeting.getMeetingDate().toString() : "",
                meeting.getMeetingNumber()
        );

        Map<String, String> data = new HashMap<>();
        data.put("type", "MEETING_COMPLETED");
        data.put("meetingId", String.valueOf(meeting.getId()));
        data.put("meetingNumber", String.valueOf(meeting.getMeetingNumber()));
        if (meeting.getMeetingDate() != null) {
            data.put("meetingDate", meeting.getMeetingDate().toString());
        }
        if (meeting.getStatus() != null) {
            data.put("status", meeting.getStatus().name());
        }

        log.info("Sending meeting completed push notification for Meeting #{} to {} token(s)",
                meeting.getMeetingNumber(), tokenStrings.size());

        sendMulticastNotification(tokenStrings, title, body, data);
    }

    /**
     * Sends multicast push notifications in batches of 500 (FCM limit) and handles obsolete token cleanup.
     */
    public void sendMulticastNotification(List<String> tokens, String title, String body, Map<String, String> data) {
        if (firebaseMessaging == null) {
            log.warn("FirebaseMessaging is not initialized. Notification '{}' not sent to {} token(s)", title, tokens.size());
            return;
        }

        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        final int BATCH_SIZE = 500;
        int totalTokens = tokens.size();

        for (int i = 0; i < totalTokens; i += BATCH_SIZE) {
            List<String> batch = tokens.subList(i, Math.min(i + BATCH_SIZE, totalTokens));
            try {
                MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setSound("default")
                                        .setChannelId("meeting_notifications")
                                        .build())
                                .build())
                        .setApnsConfig(ApnsConfig.builder()
                                .setAps(Aps.builder()
                                        .setSound("default")
                                        .build())
                                .build())
                        .addAllTokens(batch);

                if (data != null && !data.isEmpty()) {
                    messageBuilder.putAllData(data);
                }

                MulticastMessage message = messageBuilder.build();
                BatchResponse response = firebaseMessaging.sendEachForMulticast(message);

                log.info("FCM batch notification sent. Success count: {}, Failure count: {}",
                        response.getSuccessCount(), response.getFailureCount());

                if (response.getFailureCount() > 0) {
                    List<SendResponse> responses = response.getResponses();
                    List<String> staleTokens = new ArrayList<>();

                    for (int j = 0; j < responses.size(); j++) {
                        SendResponse sendResponse = responses.get(j);
                        if (!sendResponse.isSuccessful()) {
                            FirebaseMessagingException ex = sendResponse.getException();
                            String token = batch.get(j);
                            if (ex != null && (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                                    || ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                                staleTokens.add(token);
                            }
                        }
                    }

                    if (!staleTokens.isEmpty()) {
                        cleanUpStaleTokens(staleTokens);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send FCM multicast notification batch: {}", e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void cleanUpStaleTokens(List<String> staleTokens) {
        log.info("Cleaning up {} unregistered or invalid FCM tokens from database", staleTokens.size());
        for (String token : staleTokens) {
            try {
                fcmTokenRepository.deleteByFcmToken(token);
            } catch (Exception e) {
                log.warn("Failed to delete stale FCM token '{}': {}", token, e.getMessage());
            }
        }
    }
}
