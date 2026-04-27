package com.bidking.service;

import com.bidking.entity.GameConfig;
import com.bidking.mapper.GameConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 全局游戏配置管理
 * 配置只有一行（id=1），创建房间时做快照
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameConfigService {

    private final GameConfigMapper gameConfigMapper;

    public GameConfig get() {
        GameConfig config = gameConfigMapper.selectById(1);
        if (config == null) {
            log.info("[Config] 配置不存在，初始化默认配置");
            config = new GameConfig();
            gameConfigMapper.insert(config);
        }
        return config;
    }

    public GameConfig update(GameConfig config) {
        config.setId(1);
        gameConfigMapper.updateById(config);
        log.info("[Config] 配置已更新 playerCount={} blindBidding={} fuzzyFeedback={}",
                config.getPlayerCount(), config.getBlindBidding(), config.getFuzzyFeedback());
        return get();
    }
}
