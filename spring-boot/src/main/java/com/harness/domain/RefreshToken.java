package com.harness.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Refresh Token 저장 엔티티
 *
 * [Refresh Token Rotation 전략]
 * 1. 로그인 시 Refresh Token 발급 → DB 저장
 * 2. Access Token 재발급 요청 시 DB의 토큰과 대조
 * 3. 검증 성공 → 기존 토큰 삭제 + 새 토큰 발급 (rotation)
 * 4. 로그아웃 시 DB에서 삭제
 *
 * 이렇게 하면 Refresh Token 탈취 시 최대 1회만 사용 가능
 */
@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_user_id", columnList = "user_id"),
        @Index(name = "idx_refresh_token_value", columnList = "token_value", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_value", nullable = false, unique = true, length = 500)
    private String tokenValue;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public RefreshToken(Long userId, String tokenValue, LocalDateTime expiresAt) {
        this.userId     = userId;
        this.tokenValue = tokenValue;
        this.expiresAt  = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
