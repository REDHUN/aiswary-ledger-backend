package com.redhun.aiswarya_ledger_api.service;

import com.google.firebase.messaging.*;
import com.redhun.aiswarya_ledger_api.domain.entity.FcmToken;
import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
import com.redhun.aiswarya_ledger_api.repository.FcmTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private NotificationService notificationService;

    private Meeting meeting;

    @BeforeEach
    public void setUp() {
        meeting = Meeting.builder()
                .id(100L)
                .meetingNumber(15)
                .meetingDate(LocalDate.of(2026, 10, 4))
                .status(MeetingStatus.COMPLETED)
                .build();
    }

    @Test
    public void testSendMeetingCompletedNotification_NullMeeting() {
        notificationService.sendMeetingCompletedNotification(null);
        verify(fcmTokenRepository, never()).findAll();
    }

    @Test
    public void testSendMeetingCompletedNotification_NoTokensInDatabase() {
        when(fcmTokenRepository.findAll()).thenReturn(List.of());

        notificationService.sendMeetingCompletedNotification(meeting);

        verify(fcmTokenRepository, times(1)).findAll();
        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    public void testSendMeetingCompletedNotification_FirebaseMessagingNull() {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", null);

        FcmToken token1 = FcmToken.builder()
                .id(1L)
                .fcmToken("token-12345")
                .user(User.builder().id(1L).build())
                .build();

        when(fcmTokenRepository.findAll()).thenReturn(List.of(token1));

        // Should not throw exception even if firebaseMessaging is null
        assertDoesNotThrow(() -> notificationService.sendMeetingCompletedNotification(meeting));
    }

    @Test
    public void testSendMeetingCompletedNotification_Success() throws Exception {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);

        FcmToken token1 = FcmToken.builder()
                .id(1L)
                .fcmToken("token-12345")
                .user(User.builder().id(1L).build())
                .build();

        FcmToken token2 = FcmToken.builder()
                .id(2L)
                .fcmToken("token-67890")
                .user(User.builder().id(2L).build())
                .build();

        when(fcmTokenRepository.findAll()).thenReturn(List.of(token1, token2));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/aiswarya-ledger/messages/12345");

        notificationService.sendMeetingCompletedNotification(meeting);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, times(2)).send(captor.capture());

        List<Message> capturedMessages = captor.getAllValues();
        assertEquals(2, capturedMessages.size());
    }

    @Test
    public void testSendMeetingCompletedNotification_CleansUpStaleTokens() throws Exception {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);

        FcmToken token1 = FcmToken.builder()
                .id(1L)
                .fcmToken("stale-token-123")
                .user(User.builder().id(1L).build())
                .build();

        when(fcmTokenRepository.findAll()).thenReturn(List.of(token1));

        FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
        when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(mockException);

        notificationService.sendMeetingCompletedNotification(meeting);

        verify(fcmTokenRepository, times(1)).deleteByFcmToken("stale-token-123");
    }

    @Test
    public void testSendTestNotification_Success() throws Exception {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);

        FcmToken token = FcmToken.builder().id(1L).fcmToken("test-device-token").build();
        when(fcmTokenRepository.findByUserId(1L)).thenReturn(List.of(token));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/aiswarya-ledger/messages/999");

        com.redhun.aiswarya_ledger_api.dto.request.SendTestNotificationRequest req =
                com.redhun.aiswarya_ledger_api.dto.request.SendTestNotificationRequest.builder()
                        .userId(1L)
                        .title("Test Title")
                        .body("Test Body")
                        .build();

        com.redhun.aiswarya_ledger_api.dto.response.NotificationTestResponse res =
                notificationService.sendTestNotification(req, 1L);

        assertNotNull(res);
        assertEquals(1, res.getTotalTargeted());
        assertEquals(1, res.getSuccessCount());
        assertEquals("SUCCESS", res.getStatus());
    }

    @Test
    public void testSendTestNotification_NoTokensFound() {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);
        when(fcmTokenRepository.findByUserId(99L)).thenReturn(List.of());

        com.redhun.aiswarya_ledger_api.dto.request.SendTestNotificationRequest req =
                com.redhun.aiswarya_ledger_api.dto.request.SendTestNotificationRequest.builder()
                        .userId(99L)
                        .build();

        com.redhun.aiswarya_ledger_api.dto.response.NotificationTestResponse res =
                notificationService.sendTestNotification(req, 99L);

        assertNotNull(res);
        assertEquals("NO_TOKENS_FOUND", res.getStatus());
        assertEquals(0, res.getTotalTargeted());
    }
}
