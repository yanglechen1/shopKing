package com.bidking.controller;

import com.bidking.annotation.RateLimit;
import com.bidking.entity.GameConfig;
import com.bidking.service.GameConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 全局游戏配置管理
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class GameConfigController {

    private final GameConfigService gameConfigService;

    /** 查询当前全局配置 */
    @RateLimit("getGlobalConfig")
    @GetMapping
    public ResponseEntity<GameConfig> get() {
        return ResponseEntity.ok(gameConfigService.get());
    }

    /** 更新全局配置（管理员操作） */
    @RateLimit("updateGlobalConfig")
    @PutMapping
    public ResponseEntity<GameConfig> update(@RequestBody GameConfig config) {
        return ResponseEntity.ok(gameConfigService.update(config));
    }
}
