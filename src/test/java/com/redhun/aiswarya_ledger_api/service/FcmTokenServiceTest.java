package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.FcmToken;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.domain.enums.UserRole;
import com.redhun.aiswarya_ledger_api.dto.request.SaveFcmTokenRequest;
import com.redhun.aiswarya_ledger_api.dto.response.FcmTokenDto;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.FcmTokenRepository;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FcmTokenServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FcmTokenService fcmTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .passwordHash("password")
                .role(UserRole.MEMBER)
                .isActive(true)
                .build();
    }

    @Test
    void saveOrUpdateToken_whenNewToken_createsAndSaves() {
        SaveFcmTokenRequest request = SaveFcmTokenRequest.builder()
                .fcmToken("fcm-token-12345")
                .deviceType("ANDROID")
                .build();

        FcmToken savedToken = FcmToken.builder()
                .id(10L)
                .user(testUser)
                .fcmToken(request.getFcmToken())
                .deviceType(request.getDeviceType())
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fcmTokenRepository.findByFcmToken(request.getFcmToken())).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenReturn(savedToken);

        FcmTokenDto result = fcmTokenService.saveOrUpdateToken(1L, request);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals("fcm-token-12345", result.getFcmToken());
        assertEquals("ANDROID", result.getDeviceType());
        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    void saveOrUpdateToken_whenExistingToken_updatesUserAndDevice() {
        User oldUser = User.builder().id(2L).username("olduser").build();
        FcmToken existingToken = FcmToken.builder()
                .id(10L)
                .user(oldUser)
                .fcmToken("fcm-token-12345")
                .deviceType("IOS")
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .build();

        SaveFcmTokenRequest request = SaveFcmTokenRequest.builder()
                .fcmToken("fcm-token-12345")
                .deviceType("WEB")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fcmTokenRepository.findByFcmToken(request.getFcmToken())).thenReturn(Optional.of(existingToken));
        when(fcmTokenRepository.save(existingToken)).thenReturn(existingToken);

        FcmTokenDto result = fcmTokenService.saveOrUpdateToken(1L, request);

        assertNotNull(result);
        assertEquals(1L, existingToken.getUser().getId());
        assertEquals("WEB", existingToken.getDeviceType());
        verify(fcmTokenRepository).save(existingToken);
    }

    @Test
    void saveOrUpdateToken_whenUserNotFound_throwsException() {
        SaveFcmTokenRequest request = SaveFcmTokenRequest.builder()
                .fcmToken("fcm-token-12345")
                .build();

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> fcmTokenService.saveOrUpdateToken(99L, request));
        verify(fcmTokenRepository, never()).save(any());
    }

    @Test
    void deleteToken_deletesByUserIdAndToken() {
        fcmTokenService.deleteToken(1L, "fcm-token-12345");
        verify(fcmTokenRepository).deleteByUserIdAndFcmToken(1L, "fcm-token-12345");
    }

    @Test
    void getTokensByUserId_returnsTokenList() {
        FcmToken token = FcmToken.builder()
                .id(10L)
                .user(testUser)
                .fcmToken("fcm-token-12345")
                .deviceType("ANDROID")
                .build();

        when(fcmTokenRepository.findByUserId(1L)).thenReturn(List.of(token));

        List<FcmTokenDto> list = fcmTokenService.getTokensByUserId(1L);

        assertEquals(1, list.size());
        assertEquals("fcm-token-12345", list.get(0).getFcmToken());
    }
}
