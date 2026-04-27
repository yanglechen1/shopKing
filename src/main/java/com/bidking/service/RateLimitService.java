package com.bidking.service;

import com.bidking.config.RateLimitProperties;
import com.bidking.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 限流服务，基于 Redis INCR + EXPIRE 的滑动窗口计数
 *
 * 提供两种调用方式：
 * - {@link #check(String, String)}：超出限制时抛异常（供 AOP 切面使用）
 * - {@link #tryAcquire(String, String)}：超出限制时返回 false（供 WS 手动调用）
 *
 * Redis Key 格式：rate:limit:{action}:{identity}:{window}
 * 其中 window = 当前时间戳 / 窗口秒数，窗口变化后自动重新计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    private static final String KEY_PREFIX = "rate:limit:";

    /**
     * 限流检查（抛异常版），供 {@link com.bidking.config.RateLimitAspect} 使用
     *
     * @param action   限流规则标识（如 "createRoom"）
     * @param identity 身份标识（用户ID 或 IP）
     * @throws RateLimitException 超出限制时抛出
     */
    public void check(String action, String identity) {
        RateLimitProperties.Rule rule = properties.getRule(action);
        if (rule == null) return; // 未配置规则 = 不限流

        long window = System.currentTimeMillis() / 1000 / rule.getWindow();
        String key = KEY_PREFIX + action + ":" + identity + ":" + window;

        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            // Redis 异常时降级放行，避免影响正常游戏
            log.warn("[RateLimit] Redis increment 返回 null，降级放行 action={}", action);
            return;
        }
        if (count == 1) {
            // 首次创建 key 时设置过期时间
            redis.expire(key, rule.getWindow(), TimeUnit.SECONDS);
        }
        if (count > rule.getMax()) {
            log.warn("[RateLimit] 触发限流 action={} identity={} count={} max={}",
                    action, identity, count, rule.getMax());
            throw new RateLimitException("操作过于频繁，请稍后再试");
        }
    }

    /**
     * 限流检查（布尔版），供 WS 处理器手动调用
     *
     * @param action   限流规则标识
     * @param identity 身份标识
     * @return true=通过，false=被限流
     */
    public boolean tryAcquire(String action, String identity) {
        try {
            check(action, identity);
            return true;
        } catch (RateLimitException e) {
            log.warn("[RateLimit] WS 限流 action={} identity={}", action, identity);
            return false;
        }
    }
}
