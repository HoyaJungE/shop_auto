package com.harness.dto.auth;

/**
 * 로그인 / 회원가입 성공 응답
 */
public record AuthResponse(
        Long   userId,
        String email,
        String role,
        String accessToken,
        String refreshToken
) {}
