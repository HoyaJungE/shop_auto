package com.harness.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 도메인/비즈니스 예외 기반 클래스
 *
 * 사용 예:
 *   throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 *   throw new BusinessException(ErrorCode.USER_NOT_FOUND, "ID: " + id + " 사용자 없음");
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
