package com.bidking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置映射类，读取 application.yml 中 rate-limit.rules 段
 *
 * 配置示例：
 * <pre>
 * rate-limit:
 *   rules:
 *     createRoom:    { max: 3,  window: 60 }   # 创建房间：每60秒最多3次
 *     joinRoom:      { max: 10, window: 60 }   # 加入房间：每60秒最多10次
 * </pre>
 *
 * 未在 rules 中定义的 action 默认不限流（由 {@link RateLimitAspect} 兜底）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** 限流规则表，key=规则标识，value=限流参数 */
    private Map<String, Rule> rules = new HashMap<>();

    @Data
    public static class Rule {
        /** 时间窗口内允许的最大请求次数 */
        private int max;

        /** 时间窗口大小（秒） */
        private int window;
    }

    /**
     * 获取指定规则的限流参数，如果未配置则返回 null（表示不限流）
     */
    public Rule getRule(String action) {
        return rules.get(action);
    }
}
