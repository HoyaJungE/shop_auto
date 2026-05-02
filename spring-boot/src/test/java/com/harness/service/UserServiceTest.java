package com.harness.service;

import com.harness.domain.User;
import com.harness.dto.auth.AuthResponse;
import com.harness.dto.auth.LoginRequest;
import com.harness.dto.auth.SignupRequest;
import com.harness.exception.BusinessException;
import com.harness.exception.ErrorCode;
import com.harness.repository.UserRepository;
import com.harness.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService 테스트")
class UserServiceTest {

    @Mock UserRepository    userRepository;
    @Mock PasswordEncoder   passwordEncoder;
    @Mock JwtProvider       jwtProvider;
    @Mock TokenService      tokenService;

    @InjectMocks UserService userService;

    private static final String EMAIL    = "test@example.com";
    private static final String RAW_PW   = "password123";
    private static final String ENC_PW   = "$2a$10$encodedpassword";
    private static final String ACCESS_T = "access-token";
    private static final String REFRESH_T= "refresh-token";

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(RAW_PW)).willReturn(ENC_PW);
        given(jwtProvider.generateAccessToken(nullable(Long.class), anyString())).willReturn(ACCESS_T);
        given(jwtProvider.generateRefreshToken(nullable(Long.class))).willReturn(REFRESH_T);
        willDoNothing().given(tokenService).saveRefreshToken(nullable(Long.class), anyString());
    }

    // ── 회원가입 ────────────────────────────────────────────────

    @Test
    @DisplayName("정상 회원가입 시 AuthResponse를 반환한다")
    void signup_success() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AuthResponse res = userService.signup(new SignupRequest(EMAIL, RAW_PW));

        assertThat(res.email()).isEqualTo(EMAIL);
        assertThat(res.accessToken()).isEqualTo(ACCESS_T);
        assertThat(res.refreshToken()).isEqualTo(REFRESH_T);
        assertThat(res.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 USER_ALREADY_EXISTS 예외를 던진다")
    void signup_duplicateEmail_throwsException() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> userService.signup(new SignupRequest(EMAIL, RAW_PW)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_ALREADY_EXISTS));
    }

    // ── 로그인 ──────────────────────────────────────────────────

    @Test
    @DisplayName("정상 로그인 시 AuthResponse를 반환한다")
    void login_success() {
        User user = User.create(EMAIL, ENC_PW);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PW, ENC_PW)).willReturn(true);

        AuthResponse res = userService.login(new LoginRequest(EMAIL, RAW_PW));

        assertThat(res.email()).isEqualTo(EMAIL);
        assertThat(res.accessToken()).isEqualTo(ACCESS_T);
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 LOGIN_FAILED 예외를 던진다")
    void login_emailNotFound_throwsException() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequest(EMAIL, RAW_PW)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.LOGIN_FAILED));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 LOGIN_FAILED 예외를 던진다")
    void login_wrongPassword_throwsException() {
        User user = User.create(EMAIL, ENC_PW);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PW, ENC_PW)).willReturn(false);

        assertThatThrownBy(() -> userService.login(new LoginRequest(EMAIL, RAW_PW)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.LOGIN_FAILED));
    }
}
