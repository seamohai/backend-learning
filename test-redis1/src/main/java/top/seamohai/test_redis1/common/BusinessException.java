package top.seamohai.test_redis1.common;

import lombok.Getter;

/**
 * 业务异常
 *
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}