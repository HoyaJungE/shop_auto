package com.harness.security;

import com.harness.exception.BusinessException;
import com.harness.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급 / 검증 유틸
 *
 * [개선사항]
 * - Access Token 만료: 30분 → 15분 (보안 강화)
 * - jti(JWT ID) 클레임 추가 → 토큰 고유 식별 / 블랙리스트 지원
 * - iss(issuer) 클레임 추가 → 위조 토큰 검증 강화
 * - token_type 클레임 추가 → access/refresh 혼용 공격 방지
 */
@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ── 발급 ────────────────────────────────────────────────────────────────

    public String generateAccessToken(Long userId, String role) {
        return buildToken(userId, role, "access", accessTokenExpiryMs);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, "refresh", refreshTokenExpiryMs);
    }

    private String buildToken(Long userId, String role, String tokenType, long expiryMs) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())        // jti: 고유 ID → 블랙리스트 활용
                .issuer(issuer)                          // iss: 발급자 검증
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .claim("token_type", tokenType)          // access/refresh 혼용 방지
                .signWith(key);

        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }

    // ── 검증 / 파싱 ──────────────────────────────────────────────────────────

    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)               // issuer 검증
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * Access Token 전용 파싱 — refresh 토큰으로 인증 우회 방지
     */
    public Claims parseAccessToken(String token) {
        Claims claims = parseAndValidate(token);
        if (!"access".equals(claims.get("token_type"))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return claims;
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
