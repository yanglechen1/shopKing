package com.bidking.exception;

/**
 * 限流异常，被 GlobalExceptionHandler 捕获后返回 HTTP 429 Too Many Requests
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
