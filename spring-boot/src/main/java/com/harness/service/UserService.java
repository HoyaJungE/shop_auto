package com.harness.service;

import com.harness.domain.User;
import com.harness.dto.auth.AuthResponse;
import com.harness.dto.auth.LoginRequest;
import com.harness.dto.auth.SignupRequest;
import com.harness.exception.BusinessException;
import com.harness.exception.ErrorCode;
import com.harness.repository.UserRepository;
import com.harness.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 인증 서비스
 *
 * [흐름]
 * 회원가입: 이메일 중복 확인 → 비밀번호 해시 → User 저장 → 토큰 발급
 * 로그인:   이메일 조회 → 비밀번호 검증 → 토큰 발급
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository    userRepository;
    private final PasswordEncoder   passwordEncoder;
    private final JwtProvider       jwtProvider;
    private final TokenService      tokenService;

    // ── 회원가입 ────────────────────────────────────────────────

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        String encoded = passwordEncoder.encode(req.password());
        User user = User.create(req.email(), encoded);
        userRepository.save(user);

        log.info("[Auth] 회원가입 완료: userId={}, email={}", user.getId(), user.getEmail());
        return issueTokens(user);
    }

    // ── 로그인 ──────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        log.info("[Auth] 로그인 성공: userId={}, email={}", user.getId(), user.getEmail());
        return issueTokens(user);
    }

    // ── 내부: 토큰 발급 ──────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        String role         = user.getRole().name();
        String accessToken  = jwtProvider.generateAccessToken(user.getId(), role);
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());
        tokenService.saveRefreshToken(user.getId(), refreshToken);

        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                role,
                accessToken,
                refreshToken
        );
    }
}
