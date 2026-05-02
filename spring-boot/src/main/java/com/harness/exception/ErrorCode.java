package com.harness.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 정의
 * 코드 네이밍 규칙: [도메인]_[동사/상태]
 * HTTP 상태코드와 에러 코드를 한 곳에서 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ────────────────────────────────────────────────────────────────
    INVALID_INPUT(HttpStatus.BAD_REQUEST,          "COMMON_001", "유효하지 않은 입력입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND,       "COMMON_002", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,          "COMMON_004", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN,                "COMMON_005", "접근 권한이 없습니다."),

    // ── 공통 (추가) ──────────────────────────────────────────────────────────
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_006", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    // ── 인증 ────────────────────────────────────────────────────────────────
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,              "AUTH_001", "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED,              "AUTH_002", "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED,               "AUTH_003", "이메일 또는 비밀번호가 올바르지 않습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED,      "AUTH_004", "유효하지 않은 Refresh Token입니다."),

    // ── 사용자 ─────────────────────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,           "USER_001", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT,       "USER_002", "이미 존재하는 사용자입니다."),

    // ── 파일 ────────────────────────────────────────────────────────────────
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_001", "파일 업로드에 실패했습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND,           "FILE_002", "파일을 찾을 수 없습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST,     "FILE_003", "파일 크기가 허용 한도를 초과했습니다."),

    // ── 상품 등록 ────────────────────────────────────────────────────────────
    NOT_FOUND(HttpStatus.NOT_FOUND,                "REG_001", "요청한 리소스를 찾을 수 없습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST,       "REG_002", "유효하지 않은 요청입니다.");

    // ────────────────────────────────────────────────────────────────────────

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
