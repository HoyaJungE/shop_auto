package com.harness.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공통 API 응답 래퍼
 * 모든 REST 응답은 이 형태로 반환한다.
 *
 * 성공: { "success": true, "data": { ... }, "timestamp": "..." }
 * 실패: { "success": false, "error": { "code": "...", "message": "..." }, "timestamp": "..." }
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetail error;
    private LocalDateTime timestamp = LocalDateTime.now();

    // ── 성공 ────────────────────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> res = new ApiResponse<>();
        res.success = true;
        res.data    = data;
        return res;
    }

    public static ApiResponse<Void> ok() {
        ApiResponse<Void> res = new ApiResponse<>();
        res.success = true;
        return res;
    }

    // ── 실패 ────────────────────────────────────────────────────────────────

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> res = new ApiResponse<>();
        res.success = false;
        res.error   = new ErrorDetail(code, message);
        return res;
    }

    // ── Inner ────────────────────────────────────────────────────────────────

    @Getter
    public static class ErrorDetail {
        private final String code;
        private final String message;

        public ErrorDetail(String code, String message) {
            this.code    = code;
            this.message = message;
        }
    }
}
