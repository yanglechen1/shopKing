package com.bidking.controller;

import com.bidking.annotation.RateLimit;
import com.bidking.entity.GameConfig;
import com.bidking.entity.RegionPreset;
import com.bidking.entity.ThemePreset;
import com.bidking.mapper.RegionPresetMapper;
import com.bidking.mapper.ThemePresetMapper;
import com.bidking.service.RoomManager;
import com.bidking.service.SkillEngine;
import com.bidking.enums.GameState;
import com.bidking.dto.GameRoom;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final ObjectMapper objectMapper;
    private final RegionPresetMapper regionPresetMapper;
    private final ThemePresetMapper themePresetMapper;

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
        GameRoom room = roomManager.getRoom(roomId);
        return ResponseEntity.ok(sanitizeRoom(room));
    }

    /**
     * 脱敏房间数据，防止权重/价值泄露给客户端
     * - 移除 warehouse 每件物品的 value
     * - 移除 config 中的 warehouseRegions/warehouseThemes（权重数据）
     * - 注入 regionOptions/themeOptions（仅 key+name）
     */
    private Map<String, Object> sanitizeRoom(GameRoom room) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("roomId", room.getRoomId());
        map.put("state", room.getState());
        map.put("currentRound", room.getCurrentRound());
        map.put("roundDeadline", room.getRoundDeadline());

        // config 转为 Map 后再移除敏感字段
        Map<String, Object> configMap = objectMapper.convertValue(room.getConfig(), Map.class);
        configMap.remove("warehouseRegions");
        configMap.remove("warehouseThemes");
        map.put("config", configMap);

        map.put("hostId", room.getHostId());
        map.put("playerIds", room.getPlayerIds());
        map.put("playerCharacters", room.getPlayerCharacters());
        map.put("readyPlayerIds", room.getReadyPlayerIds());
        map.put("tieBreakCount", room.getTieBreakCount());

        // 注入脱敏后的地区/主题选项（从 DB 预设表读取，仅 key + name）
        map.put("regionOptions", loadRegionOptions());
        map.put("themeOptions", loadThemeOptions());

        if (room.getWarehouse() != null) {
            List<Map<String, Object>> safeWarehouse = new java.util.ArrayList<>();
            for (Map<String, Object> item : room.getWarehouse()) {
                Map<String, Object> safeItem = new java.util.LinkedHashMap<>(item);
                safeItem.remove("value");
                safeWarehouse.add(safeItem);
            }
            map.put("warehouse", safeWarehouse);
        } else {
            map.put("warehouse", null);
        }
        return map;
    }

    /** 从 region_preset 表加载地区选项（仅 key + name） */
    private List<Map<String, String>> loadRegionOptions() {
        return regionPresetMapper.selectList(null).stream()
                .map(r -> Map.of("key", r.getRegionKey(), "name", r.getName()))
                .collect(Collectors.toList());
    }

    /** 从 theme_preset 表加载主题选项（仅 key + name） */
    private List<Map<String, String>> loadThemeOptions() {
        return themePresetMapper.selectList(null).stream()
                .map(t -> Map.of("key", t.getThemeKey(), "name", t.getName()))
                .collect(Collectors.toList());
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
        if (room.getState() == GameState.WAITING || room.getState() == GameState.FINISHED) {
            return ResponseEntity.badRequest().body(Map.of("error", "当前不可使用技能"));
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
            return ResponseEntity.badRequest().body(Map.of("error", "技能在本局游戏中已使用"));
        }
        Map<String, Object> result = skillEngine.useSkill(character, room.getWarehouse(), category);
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
