package com.harness.service;

import com.harness.domain.RefreshToken;
import com.harness.domain.User;
import com.harness.exception.BusinessException;
import com.harness.exception.ErrorCode;
import com.harness.repository.RefreshTokenRepository;
import com.harness.repository.UserRepository;
import com.harness.security.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Refresh Token 생명주기 관리
 *
 * [Refresh Token Rotation 흐름]
 * 1. 로그인 성공 → saveRefreshToken()
 * 2. Access Token 만료 → rotate() 호출
 *    - 기존 Refresh Token DB에서 검증 및 삭제
 *    - 새 Access Token + 새 Refresh Token 발급
 * 3. 로그아웃 → revokeRefreshToken()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // ── Refresh Token 저장 ────────────────────────────────────────────────────

    @Transactional
    public void saveRefreshToken(Long userId, String refreshToken) {
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(refreshTokenExpiryMs / 1000);

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .userId(userId)
                        .tokenValue(refreshToken)
                        .expiresAt(expiresAt)
                        .build()
        );
    }

    // ── Rotation: 검증 + 새 토큰 발급 ────────────────────────────────────────

    @Transactional
    public TokenPair rotate(String oldRefreshToken) {
        // 1. DB에서 토큰 조회
        RefreshToken stored = refreshTokenRepository.findByTokenValue(oldRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        // 2. 만료 검사
        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        // 3. JWT 파싱 검증 (서명 등)
        Claims claims = jwtProvider.parseAndValidate(oldRefreshToken);
        Long userId   = jwtProvider.getUserId(claims);

        // 4. 사용자 역할 조회 (항상 최신 role 반영)
        String userRole = userRepository.findById(userId)
                .map(user -> user.getRole().name())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 5. 기존 토큰 삭제 (rotation)
        refreshTokenRepository.delete(stored);

        // 6. 새 토큰 발급
        String newAccessToken  = jwtProvider.generateAccessToken(userId, userRole);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);
        saveRefreshToken(userId, newRefreshToken);

        log.debug("[TokenService] Refresh Token rotated for userId={}", userId);
        return new TokenPair(newAccessToken, newRefreshToken);
    }

    // ── 로그아웃 ──────────────────────────────────────────────────────────────

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        refreshTokenRepository.findByTokenValue(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    /** 사용자의 모든 Refresh Token 폐기 (비밀번호 변경, 계정 정지 등) */
    @Transactional
    public void revokeAllByUser(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // ── 스케줄러: 만료 토큰 정리 ─────────────────────────────────────────────

    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
    @Transactional
    public void cleanExpiredTokens() {
        refreshTokenRepository.deleteAllExpired(LocalDateTime.now());
        log.info("[TokenService] 만료된 Refresh Token 정리 완료");
    }

    // ── Inner ─────────────────────────────────────────────────────────────────

    public record TokenPair(String accessToken, String refreshToken) {}
}
