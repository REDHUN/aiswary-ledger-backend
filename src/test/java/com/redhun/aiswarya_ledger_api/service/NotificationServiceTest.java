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

        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        when(mockBatchResponse.getSuccessCount()).thenReturn(2);
        when(mockBatchResponse.getFailureCount()).thenReturn(0);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        notificationService.sendMeetingCompletedNotification(meeting);

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging, times(1)).sendEachForMulticast(captor.capture());

        MulticastMessage capturedMessage = captor.getValue();
        assertNotNull(capturedMessage);
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

        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        SendResponse mockFailedResponse = mock(SendResponse.class);
        FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);

        when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(mockFailedResponse.isSuccessful()).thenReturn(false);
        when(mockFailedResponse.getException()).thenReturn(mockException);

        when(mockBatchResponse.getSuccessCount()).thenReturn(0);
        when(mockBatchResponse.getFailureCount()).thenReturn(1);
        when(mockBatchResponse.getResponses()).thenReturn(List.of(mockFailedResponse));

        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        notificationService.sendMeetingCompletedNotification(meeting);

        verify(fcmTokenRepository, times(1)).deleteByFcmToken("stale-token-123");
    }
}
