package com.harness.controller;

import com.harness.config.RateLimitConfig;
import com.harness.dto.ApiResponse;
import com.harness.dto.auth.AuthResponse;
import com.harness.dto.auth.LoginRequest;
import com.harness.dto.auth.SignupRequest;
import com.harness.dto.auth.TokenRefreshRequest;
import com.harness.exception.BusinessException;
import com.harness.exception.ErrorCode;
import com.harness.service.TokenService;
import com.harness.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 API
 *
 * POST /api/v1/auth/signup   — 회원가입
 * POST /api/v1/auth/login    — 로그인 (IP 기반 Rate Limiting: 60초당 5회)
 * POST /api/v1/auth/refresh  — Access Token 재발급 (Refresh Token Rotation)
 * POST /api/v1/auth/logout   — 로그아웃 (Refresh Token 폐기)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService    userService;
    private final TokenService   tokenService;
    private final RateLimitConfig rateLimitConfig;

    // ── 회원가입 ────────────────────────────────────────────────

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ApiResponse.ok(userService.signup(req));
    }

    // ── 로그인 ──────────────────────────────────────────────────

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpRequest) {
        // IP 기반 Rate Limiting (브루트포스 방지)
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitConfig.tryConsume(clientIp)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return ApiResponse.ok(userService.login(req));
    }

    // ── Access Token 재발급 ─────────────────────────────────────

    @PostMapping("/refresh")
    public ApiResponse<TokenService.TokenPair> refresh(
            @Valid @RequestBody TokenRefreshRequest req) {
        TokenService.TokenPair pair = tokenService.rotate(req.refreshToken());
        return ApiResponse.ok(pair);
    }

    // ── 로그아웃 ─────────────────────────────────────────────────

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody TokenRefreshRequest req) {
        tokenService.revokeRefreshToken(req.refreshToken());
        return ApiResponse.ok(null);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
