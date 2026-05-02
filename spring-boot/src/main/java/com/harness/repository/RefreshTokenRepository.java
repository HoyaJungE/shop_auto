package com.harness.repository;

import com.harness.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenValue(String tokenValue);

    /** 사용자의 모든 Refresh Token 삭제 (로그아웃 / 비밀번호 변경 시) */
    void deleteAllByUserId(Long userId);

    /** 만료된 토큰 정리 (스케줄러에서 주기적으로 호출) */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteAllExpired(LocalDateTime now);
}
