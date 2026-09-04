package com.redhun.aiswarya_ledger_api.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.redhun.aiswarya_ledger_api.domain.entity.FcmToken;
import com.redhun.aiswarya_ledger_api.domain.entity.Meeting;
import com.redhun.aiswarya_ledger_api.domain.entity.Notification;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.domain.enums.MeetingStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private NotificationService notificationService;

    private User user1;
    private User user2;
    private Meeting meeting;

    @BeforeEach
    public void setUp() {
        user1 = User.builder().id(1L).username("user1").isActive(true).build();
        user2 = User.builder().id(2L).username("user2").isActive(true).build();

        meeting = Meeting.builder()
                .id(100L)
                .meetingNumber(15)
                .meetingDate(LocalDate.of(2026, 10, 4))
                .status(MeetingStatus.COMPLETED)
                .build();
    }

    @Test
    public void testCreateAndSendNotification_SingleUser_Success() throws Exception {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userId(1L)
                .title("Meeting Reminder")
                .body("Monthly meeting tomorrow")
                .notificationType(NotificationType.MEETING)
                .referenceId(25L)
                .data(Map.of("key", "value"))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        Notification saved = Notification.builder()
                .id(101L)
                .user(user1)
                .title(request.getTitle())
                .body(request.getBody())
                .notificationType(request.getNotificationType())
                .referenceId(request.getReferenceId())
                .data(request.getData())
                .isRead(false)
                .createdAt(ZonedDateTime.now())
                .build();

        when(notificationRepository.saveAll(anyList())).thenReturn(List.of(saved));

        FcmToken fcmToken = FcmToken.builder().id(1L).user(user1).fcmToken("token-user1").build();
        when(fcmTokenRepository.findByUserId(1L)).thenReturn(List.of(fcmToken));

        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        when(mockBatchResponse.getSuccessCount()).thenReturn(1);
        when(mockBatchResponse.getFailureCount()).thenReturn(0);
        when(mockBatchResponse.getResponses()).thenReturn(List.of(mock(SendResponse.class)));
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        List<NotificationDto> result = notificationService.createAndSendNotification(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Meeting Reminder", result.getFirst().getTitle());
        assertEquals(NotificationType.MEETING, result.getFirst().getNotificationType());
        assertEquals(25L, result.getFirst().getReferenceId());
        assertFalse(result.getFirst().getIsRead());

        verify(notificationRepository, times(1)).saveAll(anyList());
        verify(firebaseMessaging, times(1)).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    public void testCreateAndSendNotification_Broadcast_Success() throws Exception {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .broadcast(true)
                .title("General Notice")
                .body("All members notice")
                .notificationType(NotificationType.ANNOUNCEMENT)
                .build();

        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(user1, user2));

        Notification saved1 = Notification.builder().id(101L).user(user1).title(request.getTitle()).notificationType(NotificationType.ANNOUNCEMENT).build();
        Notification saved2 = Notification.builder().id(102L).user(user2).title(request.getTitle()).notificationType(NotificationType.ANNOUNCEMENT).build();

        when(notificationRepository.saveAll(anyList())).thenReturn(List.of(saved1, saved2));

        FcmToken token1 = FcmToken.builder().id(1L).user(user1).fcmToken("token-1").build();
        FcmToken token2 = FcmToken.builder().id(2L).user(user2).fcmToken("token-2").build();
        when(fcmTokenRepository.findByUserId(1L)).thenReturn(List.of(token1));
        when(fcmTokenRepository.findByUserId(2L)).thenReturn(List.of(token2));

        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        when(mockBatchResponse.getSuccessCount()).thenReturn(2);
        when(mockBatchResponse.getFailureCount()).thenReturn(0);
        when(mockBatchResponse.getResponses()).thenReturn(List.of(mock(SendResponse.class), mock(SendResponse.class)));
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        List<NotificationDto> result = notificationService.createAndSendNotification(request);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(notificationRepository, times(1)).saveAll(anyList());
    }

    @Test
    public void testCreateAndSendNotification_UserNotFound() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userId(999L)
                .title("Reminder")
                .body("Body")
                .notificationType(NotificationType.GENERAL)
                .build();

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.createAndSendNotification(request));
    }

    @Test
    public void testGetUserNotifications() {
        Notification n1 = Notification.builder().id(1L).user(user1).title("N1").notificationType(NotificationType.MEETING).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(n1)));

        Page<NotificationDto> result = notificationService.getUserNotifications(1L, pageable);
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("N1", result.getContent().getFirst().getTitle());
    }

    @Test
    public void testGetUnreadCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(3L);

        UnreadNotificationCountDto result = notificationService.getUnreadCount(1L);
        assertNotNull(result);
        assertEquals(3L, result.getUnreadCount());
    }

    @Test
    public void testMarkAsRead_Success() {
        Notification notification = Notification.builder()
                .id(10L)
                .user(user1)
                .title("Title")
                .isRead(false)
                .build();

        when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDto result = notificationService.markAsRead(10L, 1L);
        assertNotNull(result);
        assertTrue(result.getIsRead());
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    public void testMarkAsRead_NotFound() {
        when(notificationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead(99L, 1L));
    }

    @Test
    public void testMarkAllAsRead() {
        when(notificationRepository.markAllAsReadByUserId(eq(1L), any(ZonedDateTime.class))).thenReturn(5);

        int updated = notificationService.markAllAsRead(1L);
        assertEquals(5, updated);
    }

    @Test
    public void testSendMeetingCompletedNotification_SavesDbAndSendsPush() throws Exception {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);

        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(user1, user2));

        FcmToken token1 = FcmToken.builder().id(1L).fcmToken("token-12345").user(user1).build();
        FcmToken token2 = FcmToken.builder().id(2L).fcmToken("token-67890").user(user2).build();
        when(fcmTokenRepository.findAll()).thenReturn(List.of(token1, token2));

        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        when(mockBatchResponse.getSuccessCount()).thenReturn(2);
        when(mockBatchResponse.getFailureCount()).thenReturn(0);
        when(mockBatchResponse.getResponses()).thenReturn(List.of(mock(SendResponse.class), mock(SendResponse.class)));
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        notificationService.sendMeetingCompletedNotification(meeting);

        verify(notificationRepository, times(1)).saveAll(anyList());
        verify(firebaseMessaging, times(1)).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    public void testSendMeetingCompletedNotification_NullMeeting() {
        notificationService.sendMeetingCompletedNotification(null);
        verify(notificationRepository, never()).saveAll(anyList());
        verify(fcmTokenRepository, never()).findAll();
    }

    @Test
    public void testSendTestNotification_Success() throws Exception {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);

        FcmToken token = FcmToken.builder().id(1L).fcmToken("test-device-token").build();
        when(fcmTokenRepository.findByUserId(1L)).thenReturn(List.of(token));

        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        when(mockBatchResponse.getSuccessCount()).thenReturn(1);
        when(mockBatchResponse.getFailureCount()).thenReturn(0);
        when(mockBatchResponse.getResponses()).thenReturn(List.of(mock(SendResponse.class)));
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        SendTestNotificationRequest req = SendTestNotificationRequest.builder()
                .userId(1L)
                .title("Test Title")
                .body("Test Body")
                .build();

        NotificationTestResponse res = notificationService.sendTestNotification(req, 1L);

        assertNotNull(res);
        assertEquals(1, res.getTotalTargeted());
        assertEquals(1, res.getSuccessCount());
        assertEquals("SUCCESS", res.getStatus());
    }

    @Test
    public void testSendTestNotification_NoTokensFound() {
        ReflectionTestUtils.setField(notificationService, "firebaseMessaging", firebaseMessaging);
        when(fcmTokenRepository.findByUserId(99L)).thenReturn(List.of());

        SendTestNotificationRequest req = SendTestNotificationRequest.builder()
                .userId(99L)
                .build();

        NotificationTestResponse res = notificationService.sendTestNotification(req, 99L);

        assertNotNull(res);
        assertEquals("NO_TOKENS_FOUND", res.getStatus());
        assertEquals(0, res.getTotalTargeted());
    }
}
