package com.bidking.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解，标注在 REST Controller 方法上
 *
 * 通过 AOP 切面拦截，基于 Redis INCR + EXPIRE 实现滑动窗口计数。
 * 未登录的接口（如登录/注册）自动降级为按 IP 限流。
 * 限流规则在 application.yml 中配置（可热加载）。
 *
 * 使用示例：
 * <pre>
 * &#64;RateLimit("createRoom")
 * &#64;PostMapping("/create")
 * public ResponseEntity<?> create(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流规则标识，对应 application.yml 中 rate-limit.rules 的 key
     * 例如 "createRoom"、"joinRoom" 等，用于从配置中读取 max 和 window
     */
    String value();
}
