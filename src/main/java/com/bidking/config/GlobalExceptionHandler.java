package com.bidking.config;

import com.bidking.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一拦截并返回友好的错误信息给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数错误（如无效的房间ID、道具类型） */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArg(IllegalArgumentException e) {
        log.warn("[API] 参数错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /** 状态错误（如游戏已开始不可操作） */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
        log.warn("[API] 状态错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /**
     * 限流拦截（HTTP 429 Too Many Requests）
     * 当用户请求频率超过 rate-limit.rules 中配置的限制时触发
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<String> handleRateLimit(RateLimitException e) {
        log.warn("[API] 限流: {}", e.getMessage());
        return ResponseEntity.status(429)
                .header("Retry-After", "60")
                .body(e.getMessage());
    }
}
