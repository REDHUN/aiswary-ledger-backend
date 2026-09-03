package com.redhun.aiswarya_ledger_api.repository;

import com.redhun.aiswarya_ledger_api.domain.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByFcmToken(String fcmToken);

    List<FcmToken> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.user.id = :userId AND f.fcmToken = :fcmToken")
    void deleteByUserIdAndFcmToken(@Param("userId") Long userId, @Param("fcmToken") String fcmToken);

    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.fcmToken = :fcmToken")
    void deleteByFcmToken(@Param("fcmToken") String fcmToken);
}
