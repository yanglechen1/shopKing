package com.bidking.config;

import com.bidking.annotation.RateLimit;
import com.bidking.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP 限流切面，拦截所有标注 {@link RateLimit} 注解的 Controller 方法
 *
 * 解析流程：
 * 1. 从注解获取 action（规则标识）
 * 2. 如果请求已认证，使用用户ID作为身份标识
 * 3. 如果未认证（登录/注册），自动降级为 IP 限流
 * 4. 调用 {@link RateLimitService#check(String, String)} 执行 Redis 计数
 * 5. 超限时抛出 {@link com.bidking.exception.RateLimitException}，由 GlobalExceptionHandler 返回 429
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String action = rateLimit.value();
        String identity = resolveIdentity();

        rateLimitService.check(action, identity);

        return pjp.proceed();
    }

    /**
     * 解析请求身份标识
     * 优先取登录用户ID，未登录时取客户端 IP
     */
    private String resolveIdentity() {
        // 优先从 SecurityContext 取已登录用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal().toString())) {
            return String.valueOf(auth.getPrincipal());
        }

        // 未登录时降级为 IP 限流
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getRemoteAddr();
            if (ip != null && !ip.isBlank()) {
                return "ip:" + ip;
            }
        }

        // 兜底（理论上不会走到这里）
        return "unknown";
    }
}
