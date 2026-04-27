package com.bidking.controller;

import com.bidking.annotation.RateLimit;
import com.bidking.entity.GameConfig;
import com.bidking.service.RoomManager;
import com.bidking.service.SkillEngine;
import com.bidking.enums.GameState;
import com.bidking.dto.GameRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 房间管理接口
 * 所有端点均受 @RateLimit 限流保护，规则见 application.yml rate-limit.rules
 */
@Slf4j
@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
public class RoomController {

    private final RoomManager roomManager;
    private final SkillEngine skillEngine;
    private final StringRedisTemplate redisTemplate;

    /** 创建房间（每60秒最多3次，防刷房） */
    @RateLimit("createRoom")
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> create(Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        String roomId = roomManager.createRoom(playerId);
        log.info("[API] 玩家={} 创建房间={}", playerId, roomId);
        return ResponseEntity.ok(Map.of("roomId", roomId));
    }

    /** 加入房间（每60秒最多10次） */
    @RateLimit("joinRoom")
    @PostMapping("/{roomId}/join")
    public ResponseEntity<Void> join(@PathVariable String roomId, Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        log.info("[API] 玩家={} 加入房间={}", playerId, roomId);
        roomManager.joinRoom(roomId, playerId);
        return ResponseEntity.ok().build();
    }

    /** 开始游戏（仅房主，每60秒最多3次） */
    @RateLimit("startGame")
    @PostMapping("/{roomId}/start")
    public ResponseEntity<Void> start(@PathVariable String roomId, Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        log.info("[API] 玩家={} 请求开始游戏 roomId={}", playerId, roomId);
        roomManager.startGame(roomId, playerId);
        return ResponseEntity.ok().build();
    }

    /** 取消准备（每60秒最多10次） */
    @RateLimit("ready")
    @PostMapping("/{roomId}/unready")
    public ResponseEntity<Void> unready(@PathVariable String roomId, Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        roomManager.cancelReady(roomId, playerId);
        return ResponseEntity.ok().build();
    }

    /**
     * 玩家准备（可附带购买清单，在准备阶段一次提交到后端）
     * 请求体示例：{"purchases": ["DOUBLE_BID", "BID_INSURANCE"]}
     */
    @RateLimit("ready")
    @PostMapping("/{roomId}/ready")
    public ResponseEntity<Void> ready(@PathVariable String roomId,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        @SuppressWarnings("unchecked")
        List<String> purchases = (body != null)
                ? (List<String>) body.getOrDefault("purchases", List.of())
                : List.of();
        roomManager.setReady(roomId, playerId, purchases);
        return ResponseEntity.ok().build();
    }

    /** 查询房间状态（每60秒最多30次） */
    @RateLimit("getRoom")
    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(roomManager.getRoom(roomId));
    }

    /** 房主更新本局配置（每60秒最多5次） */
    @RateLimit("updateConfig")
    @PutMapping("/{roomId}/config")
    public ResponseEntity<Void> updateConfig(@PathVariable String roomId,
                                             @RequestBody GameConfig config,
                                             Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        roomManager.updateRoomConfig(roomId, playerId, config);
        return ResponseEntity.ok().build();
    }

    // ── 角色选择 ──────────────────────────────────────────────

    /** 选择角色（每60秒最多10次） */
    @RateLimit("selectCharacter")
    @PostMapping("/{roomId}/character")
    public ResponseEntity<Void> selectCharacter(@PathVariable String roomId,
                                                @RequestParam String type,
                                                Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        roomManager.selectCharacter(roomId, playerId, type.toUpperCase());
        return ResponseEntity.ok().build();
    }

    // ── 手动技能 ──────────────────────────────────────────────

    /**
     * 手动使用角色技能（REST，已取代旧WS处理器）
     * 艾莎需要传 category 参数：/api/room/{roomId}/skill?category=FURNITURE
     */
    @RateLimit("useSkill")
    @PostMapping("/{roomId}/skill")
    public ResponseEntity<?> useSkill(@PathVariable String roomId,
                                      @RequestParam(required = false) String category,
                                      Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        GameRoom room = roomManager.getRoom(roomId);
        if (room.getState() != GameState.SKILL_PHASE) {
            return ResponseEntity.badRequest().body(Map.of("error", "当前不在技能阶段"));
        }
        String pidStr = String.valueOf(playerId);
        String character = room.getPlayerCharacters() != null
                ? room.getPlayerCharacters().get(pidStr) : null;
        if (character == null || character.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "未选择角色"));
        }
        // 防重复使用（SETNX）
        String stateKey = "game:player:state:" + roomId + ":" + pidStr;
        Boolean set = redisTemplate.opsForHash().putIfAbsent(stateKey, "skillUsed", "true");
        if (Boolean.FALSE.equals(set)) {
            return ResponseEntity.badRequest().body(Map.of("error", "本轮技能已使用"));
        }
        Map<String, Object> result = skillEngine.useSkill(character, room.getWarehouse(), room.getCurrentRound(), category);
        // 技能执行返回 error 时，清除 SETNX 标记，允许玩家重试
        if (result.containsKey("error")) {
            redisTemplate.opsForHash().delete(stateKey, "skillUsed");
            log.warn("[Skill] 玩家={} 技能执行失败，已清除SETNX: {}", playerId, result.get("error"));
            return ResponseEntity.badRequest().body(result);
        }
        log.info("[Skill] 玩家={} 使用技能={} 结果={}", playerId, character, result);
        return ResponseEntity.ok(result);
    }

    // ── 房主管理 ──────────────────────────────────────────────

    /** 重新开始游戏（房主，需在 FINISHED 状态） */
    @RateLimit("startGame")
    @PostMapping("/{roomId}/restart")
    public ResponseEntity<Void> restart(@PathVariable String roomId, Authentication auth) {
        Long playerId = (Long) auth.getPrincipal();
        roomManager.restartGame(roomId, playerId);
        return ResponseEntity.ok().build();
    }

    /** 房主踢出玩家（每60秒最多5次） */
    @RateLimit("kickPlayer")
    @PostMapping("/{roomId}/kick/{targetId}")
    public ResponseEntity<Void> kickPlayer(@PathVariable String roomId,
                                           @PathVariable Long targetId,
                                           Authentication auth) {
        Long hostId = (Long) auth.getPrincipal();
        roomManager.kickPlayer(roomId, hostId, targetId);
        return ResponseEntity.ok().build();
    }
}
