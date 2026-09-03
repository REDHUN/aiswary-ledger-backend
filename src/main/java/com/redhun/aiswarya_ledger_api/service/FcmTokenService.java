package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.FcmToken;
import com.redhun.aiswarya_ledger_api.domain.entity.User;
import com.redhun.aiswarya_ledger_api.dto.request.SaveFcmTokenRequest;
import com.redhun.aiswarya_ledger_api.dto.response.FcmTokenDto;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.FcmTokenRepository;
import com.redhun.aiswarya_ledger_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public FcmTokenDto saveOrUpdateToken(Long userId, SaveFcmTokenRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        FcmToken token = fcmTokenRepository.findByFcmToken(request.getFcmToken())
                .map(existingToken -> {
                    log.info("Updating existing FCM token (id={}) to user ID: {}", existingToken.getId(), userId);
                    existingToken.setUser(user);
                    if (request.getDeviceType() != null && !request.getDeviceType().isBlank()) {
                        existingToken.setDeviceType(request.getDeviceType());
                    }
                    return existingToken;
                })
                .orElseGet(() -> {
                    log.info("Registering new FCM token for user ID: {}", userId);
                    return FcmToken.builder()
                            .user(user)
                            .fcmToken(request.getFcmToken())
                            .deviceType(request.getDeviceType())
                            .build();
                });

        FcmToken savedToken = fcmTokenRepository.save(token);
        return mapToDto(savedToken);
    }

    @Transactional
    public void deleteToken(Long userId, String fcmToken) {
        log.info("Deleting FCM token for user ID: {}", userId);
        fcmTokenRepository.deleteByUserIdAndFcmToken(userId, fcmToken);
    }

    @Transactional(readOnly = true)
    public List<FcmTokenDto> getTokensByUserId(Long userId) {
        return fcmTokenRepository.findByUserId(userId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FcmTokenDto> getAllTokens() {
        return fcmTokenRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public void clearAllTokens() {
        log.info("Clearing all FCM tokens from database");
        fcmTokenRepository.deleteAll();
    }

    private FcmTokenDto mapToDto(FcmToken token) {
        return FcmTokenDto.builder()
                .id(token.getId())
                .userId(token.getUser().getId())
                .fcmToken(token.getFcmToken())
                .deviceType(token.getDeviceType())
                .createdAt(token.getCreatedAt())
                .updatedAt(token.getUpdatedAt())
                .build();
    }
}
