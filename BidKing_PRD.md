# BidKing 竞拍之王 — 完整需求与开发规范

> 本文档用于指导 AI（claude-opus-4-6）完成全流程开发。请严格按照本文档的设计决策实现，不要自行发明替代方案。填写必要的中文注释，以及详细的日志。

---

## 目录

1. [项目概述](#1-项目概述)
2. [游戏规则](#2-游戏规则)
3. [可配置参数体系](#3-可配置参数体系)
4. [技术栈](#4-技术栈)
5. [系统架构](#5-系统架构)
6. [数据库设计](#6-数据库设计)
7. [核心模块设计](#7-核心模块设计)
8. [网络与实时通信](#8-网络与实时通信)
9. [断线重连设计](#9-断线重连设计)
10. [出价冗余时间设计](#10-出价冗余时间设计)
11. [前端设计](#11-前端设计)
12. [服务器部署](#12-服务器部署)
13. [开发阶段划分](#13-开发阶段划分)
14. [道具系统](#14-道具系统)
15. [技能系统（当前实现）](#15-技能系统当前实现)
16. [左侧玩家面板](#16-左侧玩家面板游戏主界面)

---

## 1. 项目概述

**BidKing** 是一款网页版多人暗标竞拍游戏，基于官方《竞拍之王》玩法改编。核心差异：

- 支持 **2～8 人**（官方固定 4 人），默认 **5 人**
- 所有核心参数均可在创建房间时配置
- 私人局，面向固定玩家群体，无需高并发设计
- 后端：Java（Spring Boot 3）；前端：Vue 3

---

## 2. 游戏规则

### 2.1 核心机制

- **暗标竞拍**：每轮所有玩家独立、秘密提交出价，无法看到他人出价 （这个允许配置，不配置允许看到详细价格，包括每轮的价格）
- **模糊反馈**：出价提交后，服务端只下发"领先 / 靠后 / 远落后"等模糊信息，不暴露具体金额 （这个允许配置，不配置允许看到详细价格，包括每轮的价格）
- **信息差**：不同角色拥有不同信息获取能力，这是博弈核心

### 2.2 游戏流程

```
准备阶段（大厅）
  └─ 房主创建房间（设置 GameConfig）
  └─ 玩家加入（最多 playerCount 人）
  └─ 每位玩家在房间大厅选择角色（可重复选择同一角色）
  └─ 玩家可在商店用初始金币购买道具
  └─ 所有人选择角色并就绪后，房主开始游戏
  └─ 服务端生成仓库，初始化玩家金币/道具/角色状态

循环博弈阶段（第 1 轮 ～ 第 speedWinRounds 轮）
  └─ SKILL_PHASE：玩家通过 REST 接口手动使用技能（限时）
  └─ BIDDING：玩家提交暗标出价（可同时使用一个道具，限时 + Grace Period）
  └─ EVALUATING：服务端汇总出价，判定是否触发速胜
      └─ 触发速胜 → 跳转 REVEALING
      └─ 未触发   → 下发模糊反馈，进入下一轮

决战阶段（第 speedWinRounds + 1 轮起，共 finalRounds 轮）
  └─ 同 BIDDING，但取消倍率判定，纯价高者得

开箱阶段（REVEALING）
  └─ 物品按品质从低到高逐件揭晓（revealDelaySecs 控制节奏）
  └─ 最高品质物品有特效展示

结算阶段（SETTLING）
  └─ 利润 = Σ物品实际价值 - 中标价
  └─ 更新玩家积分、金币
  └─ 展示本局排行

平局处理
  └─ 最高价相同 → 进入 TIE_BREAK 加赛（最多 tieBreakRounds 轮）
  └─ 仍平局 → 均分仓库价值
```

### 2.3 速胜判定公式

```
第 n 轮（n 从 1 开始）：
  最高出价 P1 ≥ 次高出价 P2 × speedWinRatios[n-1]
  → 触发速胜，P1 玩家直接获得仓库
```

**5 人局倍率建议**（次高价竞争更激烈，需调低倍率）：

| 轮次 | 4 人局（官方） | 5 人局（推荐） |
|------|--------------|--------------|
| 第1轮 | 2.0×         | 1.8×         |
| 第2轮 | 1.6×         | 1.5×         |
| 第3轮 | 1.4×         | 1.3×         |
| 第4轮 | 1.2×         | 1.15×        |

### 2.4 物品体系

**品质等级（由低到高）**

| 等级 | 颜色 | 说明 |
|------|------|------|
| 普通 | 蓝色 | 基础物品，价值低 |
| 高级 | 蓝色+ | 略优于普通 |
| 稀有 | 紫色 | 核心目标，价值高 |
| 史诗 | 紫色+ | 有 3D 动效 |
| 传说 | 橙色 | 顶级 |
| 神话 | 橙色+ | 终极宝藏 |

**特殊物品**

- **黑匣子**：开启后随机，可能是巨额道具，也可能是废铁

**品类**

- 家居用品、数码产品、复古古董

### 2.5 角色体系

| 角色 | 流派 | 核心技能描述 | 触发时机 |
|------|------|------------|---------|
| 老头 | 精算流 | 开局获得紫/金品质道具总件数 | 手动（首回合推荐） |
| 艾莎 | 精算流/轮椅流 | 通过道具获取全仓格数，第四回合发力 | 手动（每回合） |
| 伊森 | 精算流 | 自带看"仓深"能力，第二三轮精确值，其他轮区间 | 手动（每回合） |
| 石油哥 | 精算流 | 每回合提供金均格，精算件数，道具偏贵 | 手动（每回合） |
| 索嗨 | 探货流 | 探查最高价值物品品质 | 手动（每回合） |
| 伊莎贝拉 | 探货流 | 开局探查最高品质物品信息（名称、件数、最大价值） | 手动（首回合推荐） |

---

## 3. 可配置参数体系

### 3.1 GameConfig 完整字段

```java
@Data
@Entity
@Table(name = "game_config")
public class GameConfig {

    // ── 玩家配置 ──────────────────────────────
    private int playerCount = 5;           // 玩家人数，2~8
    private long initialCoins = 10000;     // 每人初始金币
    private long minBid = 1;               // 最低出价下限

    // ── 轮次配置 ──────────────────────────────
    private int speedWinRounds = 4;        // 速胜窗口轮数
    private int finalRounds = 1;           // 决战轮数
    private int tieBreakRounds = 3;        // 平局加赛最大轮数

    // ── 时间配置 ──────────────────────────────
    private int skillPhaseSecs = 20;       // 技能/道具决策时间（秒）
    private int bidTimeSecs = 30;          // 出价倒计时（秒）
    private int gracePeriodMs = 3000;      // 出价冗余时间（毫秒），见第10节

    // ── 速胜倍率（List 长度须等于 speedWinRounds）──
    // 5人局推荐：[1.8, 1.5, 1.3, 1.15]
    // 4人局推荐：[2.0, 1.6, 1.4, 1.2]
    private List<Double> speedWinRatios = List.of(1.8, 1.5, 1.3, 1.15);

    // ── 仓库配置 ──────────────────────────────
    private int warehouseSizeMin = 10;     // 仓库最少格数（旧模式回退用）
    private int warehouseSizeMax = 20;     // 仓库最多格数（旧模式回退用）
    private boolean blackBoxEnabled = true;
    private int revealDelaySecs = 2;       // 开箱每件物品揭晓延迟（秒）

    // ── 品质权重（旧模式回退用）─────────────
    // key: Quality 枚举, value: 权重整数
    // 示例：{COMMON:50, RARE:30, EPIC:15, LEGEND:4, MYTH:1}
    @Convert(converter = QualityWeightConverter.class)
    private Map<Quality, Integer> qualityWeights;

    // ── 仓库主题与地区（Theme × Region 模式）──
    /** 仓库主题: Category枚举名 / "UNKNOWN" / "RANDOM" */
    private String warehouseTheme = "RANDOM";
    /** 仓库地区: Region key / "RANDOM" */
    private String warehouseRegion = "RANDOM";
    /** Region 预设 JSON 数组，可热更新品质权重和数量范围 */
    private String warehouseRegions;
    /** Theme 预设 JSON 数组，可热更新品类权重 */
    private String warehouseThemes;
}
```

### 3.2 配置快照机制

**重要**：`GameConfig` 在创建房间时做一次快照注入到 `GameRoom`，存入 Redis。中途修改全局配置不影响进行中的局，下一局才生效。

```java
// 创建房间时
GameRoom room = new GameRoom();
room.setConfig(configService.getCurrentConfig().snapshot()); // 深拷贝
```

---

## 4. 技术栈

### 4.1 后端

| 组件 | 选型 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.x |
| 语言 | Java | 17+ |
| 实时通信 | Spring WebSocket + STOMP | 内置 |
| 数据库 | MySQL | 8.x |
| 缓存/实时状态 | Redis | 7.x |
| Redis 客户端 | Redisson | 3.x（提供分布式锁） |
| ORM | MyBatis Plus | 3.x |
| 构建 | Maven | 3.x |

### 4.2 前端

| 组件 | 选型 |
|------|------|
| 框架 | Vue 3 + Vite |
| 状态管理 | Pinia |
| WebSocket | SockJS + StompJS |
| UI | 自定义（游戏风格），可用 TailwindCSS |

### 4.3 部署

| 组件 | 说明 |
|------|------|
| 反向代理 | Nginx（处理 WebSocket upgrade） |
| 进程管理 | systemd 或 screen |
| 服务器 | 2 核 2G 云服务器（腾讯云/阿里云轻量） |

---

## 5. 系统架构

```
客户端（Vue 3）
  ├─ HTTP REST（登录、配置管理、查询）
  └─ WebSocket STOMP（实时游戏消息）
       │
       ▼
Nginx（反向代理 + WebSocket upgrade）
       │
       ▼
Spring Boot 应用（8080端口）
  ├─ REST Controller 层
  ├─ WebSocket Handler 层（STOMP）
  ├─ Service 层
  │   ├─ RoomManager        房间生命周期 + 状态机
  │   ├─ BidEvaluator       暗标汇总 + 速胜判定
  │   ├─ InfoBroker          角色权限过滤 + 模糊反馈
  │   ├─ RngManager          仓库随机生成
  │   ├─ SkillEngine         角色技能执行
  │   ├─ TimerService        每轮独立倒计时（含 Grace Period）
  │   └─ RevealEngine        开箱结算
  ├─ Redis（实时游戏状态）
  └─ MySQL（持久化数据）
```

### 5.1 防作弊原则

**所有出价比价逻辑必须在服务端完成。**

- 前端永远只收到"模糊反馈"，绝不收到其他玩家的出价金额
- 仓库完整数据存在服务端，InfoBroker 按角色权限裁剪后才下发
- 出价通过 WebSocket 发送，服务端校验合法性后才记录

---

## 6. 数据库设计

### 6.1 表结构

```sql
-- 玩家表
CREATE TABLE player (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50) NOT NULL UNIQUE,
    nickname    VARCHAR(50) NOT NULL,
    password    VARCHAR(100) NOT NULL,
    total_coins BIGINT DEFAULT 0,
    total_score INT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 全局配置表（只有一行，房主可修改）
CREATE TABLE game_config (
    id                  INT PRIMARY KEY DEFAULT 1,
    player_count        INT DEFAULT 5,
    initial_coins       BIGINT DEFAULT 10000,
    min_bid             BIGINT DEFAULT 1,
    speed_win_rounds    INT DEFAULT 4,
    final_rounds        INT DEFAULT 1,
    tie_break_rounds    INT DEFAULT 3,
    skill_phase_secs    INT DEFAULT 20,
    bid_time_secs       INT DEFAULT 30,
    grace_period_ms     INT DEFAULT 3000,
    speed_win_ratios    JSON NOT NULL,        -- [1.8, 1.5, 1.3, 1.15]
    warehouse_size_min  INT DEFAULT 10,
    warehouse_size_max  INT DEFAULT 20,
    black_box_enabled   TINYINT DEFAULT 1,
    reveal_delay_secs   INT DEFAULT 2,
    quality_weights     JSON NOT NULL         -- {"COMMON":50,"RARE":30,...}
);

-- 物品字典表
CREATE TABLE item_template (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    category    ENUM('FURNITURE','DIGITAL','ANTIQUE') NOT NULL,
    quality     ENUM('COMMON','ADVANCED','RARE','EPIC','LEGEND','MYTH') NOT NULL,
    value_min   BIGINT NOT NULL,             -- 实际价值范围下限
    value_max   BIGINT NOT NULL,             -- 实际价值范围上限
    is_black_box TINYINT DEFAULT 0,
    description TEXT,
    image_url   VARCHAR(255)
);

-- 游戏记录表（每局一行）
CREATE TABLE game_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id         VARCHAR(36) NOT NULL,    -- UUID
    config_snapshot JSON NOT NULL,           -- GameConfig 快照
    winner_id       BIGINT,
    final_bid       BIGINT,
    warehouse_value BIGINT,
    profit          BIGINT,
    total_rounds    INT,
    started_at      DATETIME,
    ended_at        DATETIME
);

-- 玩家游戏参与记录
CREATE TABLE game_player_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    game_record_id  BIGINT NOT NULL,
    player_id       BIGINT NOT NULL,
    character_type  VARCHAR(50),
    final_bid       BIGINT DEFAULT 0,
    coins_spent     BIGINT DEFAULT 0,
    profit          BIGINT DEFAULT 0,
    rank            INT
);
```

### 6.2 Redis 数据结构

```
# 房间实时状态（Hash）
game:room:{roomId}
  state           STRING   当前状态枚举（WAITING/SKILL_PHASE/BIDDING/...）
  currentRound    INT      当前轮次（1-based）
  roundDeadline   LONG     本轮截止时间戳（毫秒，Unix epoch）
  configSnapshot  JSON     GameConfig 快照
  warehouse       JSON     仓库完整数据（含所有物品实际价值，绝不下发给客户端）
  TTL: 2小时（游戏结束后自动清理）

# 玩家出价（Hash，每轮覆盖）
game:bids:{roomId}:{round}
  {playerId}      LONG     出价金额（-1 表示未出价/弃权）
  TTL: 24小时

# 玩家房间映射（String，用于断线重连查找）
game:player:room:{playerId}
  value: {roomId}
  TTL: 2小时

# 玩家本局状态（Hash）
game:player:state:{roomId}:{playerId}
  character       STRING   角色类型
  coins           LONG     当前金币
  hasBidThisRound BOOLEAN  本轮是否已出价（用于重连判断）
  skillUsed       BOOLEAN  本轮技能是否已用
  items           JSON     道具列表 ["DOUBLE_BID", "PEEK_TOTAL"]
  insurance:round:{N} STRING 保险卡标记（round N 有保险）
  halfDiscount:round:{N} STRING 截胡卡标记（round N 有截胡）
  TTL: 2小时
```

---

## 7. 核心模块设计

### 7.1 游戏状态机

```java
public enum GameState {
    WAITING,        // 等待玩家加入
    SKILL_PHASE,    // 技能/道具决策阶段
    BIDDING,        // 暗标出价阶段
    GRACE_PERIOD,   // 出价冗余时间（对玩家透明，UI 显示"等待结果"）
    EVALUATING,     // 服务端判定（瞬时，不暴露给客户端）
    REVEALING,      // 开箱揭晓
    SETTLING,       // 结算
    TIE_BREAK,      // 加赛
    FINISHED        // 结束
}
```

状态转移由 `RoomManager` 统一驱动，禁止在其他 Service 中直接修改状态。

### 7.2 BidEvaluator（核心算法）

```java
public class BidEvaluator {

    /**
     * 汇总本轮出价，判定结果
     * @param bids Map<playerId, bidAmount>，未出价的玩家值为 -1
     * @param config 本局配置快照
     * @param round 当前轮次（1-based）
     * @return EvaluationResult
     */
    public EvaluationResult evaluate(Map<String, Long> bids,
                                     GameConfig config,
                                     int round) {
        // 1. 过滤弃权（-1）
        // 2. 排序，找 P1（最高价）和 P2（次高价）
        // 3. 判断是否在速胜窗口内
        // 4. 若在窗口内：P1 >= P2 * speedWinRatios[round-1] → 速胜
        // 5. 若是决战轮：直接 P1 获胜
        // 6. 若 P1 == P2：TIE_BREAK
        // 7. 否则：未触发，生成模糊反馈
    }
}
```

### 7.3 InfoBroker（信息下发）

```java
public class InfoBroker {

    /**
     * 生成下发给指定玩家的模糊反馈
     * 绝不包含他人出价金额
     */
    public BidFeedback generateFeedback(String playerId,
                                        Map<String, Long> allBids,
                                        EvaluationResult result) {
        long myBid = allBids.get(playerId);
        long p1 = result.getHighestBid();

        if (myBid == p1 && result.isUnique()) {
            return BidFeedback.LEADING;
        } else if (myBid >= p1 * 0.9) {
            return BidFeedback.CLOSE;
        } else if (myBid >= p1 * 0.5) {
            return BidFeedback.BEHIND;
        } else {
            return BidFeedback.FAR_BEHIND;
        }
    }
}
```

### 7.4 TimerService（倒计时管理）

```java
/**
 * 每个房间、每轮独立管理一个 ScheduledFuture
 * 严禁使用 @Scheduled（那是全局定时器，不能按房间隔离）
 */
@Service
public class TimerService {

    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(10); // 按最大并发房间数调整

    public void startBiddingTimer(String roomId, GameConfig config) {
        long deadlineTs = System.currentTimeMillis() + config.getBidTimeSecs() * 1000L;
        // 存入 Redis，供客户端重连时同步倒计时
        redisTemplate.opsForHash().put("game:room:" + roomId,
                                       "roundDeadline",
                                       String.valueOf(deadlineTs));

        // 倒计时到 0：进入 Grace Period
        scheduler.schedule(() -> enterGracePeriod(roomId, config),
                           config.getBidTimeSecs(), TimeUnit.SECONDS);
    }

    private void enterGracePeriod(String roomId, GameConfig config) {
        // 更新状态为 GRACE_PERIOD（对客户端不推送状态变更，UI 继续显示"等待"）
        roomManager.setState(roomId, GameState.GRACE_PERIOD);

        // Grace Period 结束后触发判定
        scheduler.schedule(() -> roomManager.triggerEvaluation(roomId),
                           config.getGracePeriodMs(), TimeUnit.MILLISECONDS);
    }
}
```

### 7.5 RngManager（仓库生成）— Theme × Region 双维模式

```java
public class RngManager {
    /**
     * Theme × Region 双维仓库生成
     * Region 决定仓库大小 + 品质分布，Theme 决定品类偏向
     * 未配置 JSON 列时回退旧模式（warehouseSizeMin/Max + qualityWeights）
     */
    public List<Map<String, Object>> generate(GameConfig config) {
        // 1. 解析 Region/Theme 预设 JSON
        List<WarehouseRegion> regions = parseRegions(config);
        List<WarehouseTheme> themes = parseThemes(config);

        if (regions 不为空 && themes 不为空) {
            // 2. 解析 region（RANDOM → 随机选，否则按 key 匹配）
            WarehouseRegion region = resolveRegion(config.getWarehouseRegion(), regions);
            // 3. 解析 theme（RANDOM → 随机选，否则按 key 匹配）
            WarehouseTheme theme = resolveTheme(config.getWarehouseTheme(), themes);

            // 4. region 提供物品数量范围和品质权重
            int size = random(region.itemCountMin, region.itemCountMax);
            Map<String,Integer> qualityWeights = region.qualityWeights;

            // 5. theme 提供品类权重
            Map<String,Integer> categoryWeights = theme.categoryWeights;

            // 6. 逐物品：品质×品类双维加权 → 精确命中模板
            for (i in size) {
                quality  = weightedRandom(qualityWeights);
                category = weightedRandom(categoryWeights);
                tpl = mapper.randomByQualityAndCategory(quality, category);
                if (tpl == null) tpl = mapper.randomByQuality(quality); // fallback
                items.add(buildItem(tpl));
            }
        } else {
            // 旧模式回退
        }

        // 7. 黑匣子 + 网格分配
        finishItems(items, config);
        return items;
    }
}
```

**Region 预设（4档，存 JSON 可热更新）：**

| Key | 名称 | 物品数 | 品质权重(白/绿/蓝/紫/金/红) |
|-----|------|--------|--------------------------|
| DELIVERY_STATION | 快递站 | 5~10 | 45/25/15/8/5/2 |
| VILLA | 别墅 | 10~18 | 35/28/18/12/5/2 |
| MUSEUM | 博物馆 | 15~25 | 8/12/18/28/22/12 |
| SHIPWRECK | 沉船 | 18~30 | 10/15/20/25/20/10 |

**Theme 预设（11个，存 JSON 可热更新）：**

10个品类各一个主题（主品类 40%，其余均分）+ UNKNOWN 未知盲盒（10品类各 10%）

---

## 8. 网络与实时通信

### 8.1 WebSocket STOMP 配置

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端订阅前缀
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 私信前缀（给单个玩家的消息）
        registry.setUserDestinationPrefix("/user");
    }
}
```

### 8.2 Topic 规范

| Topic / Endpoint | 方向 | 内容 |
|-------|------|------|
| `/topic/room/{roomId}` | 服务端→全体 | 状态变更、轮次开始、开箱帧、BID_PLACED、ALL_BIDS |
| `/user/queue/feedback` | 服务端→个人 | 模糊反馈 / 精确最高价（只有本人收到） |
| `/user/queue/reconnect` | 服务端→个人 | 重连快照（含 bidderIds 恢复左侧面板） |
| `/user/queue/error` | 服务端→个人 | 错误消息 |
| `/user/queue/item-info` | 服务端→个人 | 道具效果信息（如透视卡仓库总价值） |
| `/user/queue/inventory` | 服务端→个人 | 背包更新（道具列表、金币） |
| `/user/queue/bid-ack` | 服务端→个人 | 出价确认回执 |
| `/app/room/bid` | 客户端→服务端 | 提交出价（可携带 itemType 使用道具） |
| `POST /api/room/{roomId}/skill` | 客户端→服务端 | 手动使用技能（REST，不走 WS） |

### 8.3 消息格式

所有消息统一 JSON 格式：

```json
// 服务端广播：轮次开始
{
  "type": "ROUND_START",
  "round": 2,
  "deadlineTs": 1718000000000,
  "payload": {}
}

// 服务端私信：模糊反馈
{
  "type": "BID_FEEDBACK",
  "round": 2,
  "feedback": "BEHIND",     // LEADING / CLOSE / BEHIND / FAR_BEHIND
  "payload": {}
}

// 客户端发送：提交出价
{
  "playerId": "xxx",
  "amount": 5000,
  "itemType": "DOUBLE_BID",   // 可选，使用道具
  "clientTs": 1718000015000   // 客户端时间戳，仅日志用，判定以服务端为准
}

// 服务端广播：有玩家出价了（左侧面板状态更新）
{
  "type": "BID_PLACED",
  "playerId": "123"
}

// 服务端广播：本轮所有出价汇总（EVALUATING 时发送）
{
  "type": "ALL_BIDS",
  "payload": {
    "bids": { "1": 5000, "2": 3000, "3": -1 }    // 明拍模式：显示实际金额
    // 或
    "ranking": ["1", "2"]                          // 暗拍模式：只显示排名
  }
}

// 服务端广播：玩家出价计数
{
  "type": "BID_COUNT",
  "count": 3,
  "total": 5
}

// 服务端广播：技能阶段开始
{
  "type": "SKILL_PHASE_START",
  "payload": { "round": 2 }
}

// 服务端广播：轮次开始（BIDDING）
{
  "type": "ROUND_START",
  "payload": {
    "round": 2,
    "deadlineTs": 1718000000000
  }
}
```

---

## 9. 断线重连设计

### 9.1 核心原则

- **游戏不等人**：断线期间游戏正常推进，未出价视为弃权（出价 0）
- **状态完整保留**：所有游戏状态存在 Redis，玩家重连后可完整恢复
- **倒计时用绝对时间戳**：存 `roundDeadline`（Unix ms），不存剩余秒数，重连后客户端自行计算剩余时间，避免时间跳变

### 9.2 重连流程

```
玩家重新连接（刷新页面 / 网络恢复）
  │
  ├─ 查 Redis key: game:player:room:{playerId}
  │
  ├─ 不存在 → 返回大厅
  │
  ├─ 存在，房间已 FINISHED → 推送结算结果
  │
  └─ 存在，房间进行中
       │
       ├─ 从 Redis 读取房间完整快照
       │   (当前状态、当前轮次、roundDeadline、本人历史出价、技能状态、金币)
       │
       ├─ 通过 /user/queue/reconnect 私信下发快照
       │
       └─ 判断本轮是否已出价
           ├─ 已出价 → 静默恢复，等待本轮结果
           └─ 未出价 → 立即推送出价弹窗（含剩余时间）
```

### 9.3 实现要点

```java
@MessageMapping("/reconnect")
public void handleReconnect(@Header("simpSessionId") String sessionId,
                             Principal principal) {
    String playerId = principal.getName();
    String roomId = redisTemplate.opsForValue()
                                 .get("game:player:room:" + playerId);

    if (roomId == null) {
        messagingTemplate.convertAndSendToUser(
            playerId, "/queue/reconnect",
            ReconnectResponse.toLobby()
        );
        return;
    }

    GameRoomSnapshot snapshot = buildSnapshot(roomId, playerId);
    messagingTemplate.convertAndSendToUser(
        playerId, "/queue/reconnect", snapshot
    );
}
```

---

## 10. 出价冗余时间设计（Grace Period）

### 10.1 设计动机

- 出价倒计时在服务端到 0，但玩家出价包可能已在网络传输中（尤其网卡时）
- 直接判定 0 对网络较差的玩家不公平
- 解决方案：服务端倒计时到 0 后，再等 `gracePeriodMs`（默认 3000ms），才开始判定

### 10.2 时序图

```
客户端显示 30s 倒计时
         |
服务端计时器到 0
         |
    [进入 GRACE_PERIOD]
    ← 这 3s 内收到的出价，只要客户端是在倒计时内发出的，就接受
         |
Grace Period 结束（3s 后）
         |
    [EVALUATING] → BidEvaluator 判定
```

### 10.3 实现规则

```java
public ResponseEntity<?> submitBid(BidRequest request) {
    GameRoom room = getRoom(request.getRoomId());
    long now = System.currentTimeMillis();

    // 服务端接收时间判定，不信任客户端时间戳
    long deadline = room.getRoundDeadline();
    long graceCutoff = deadline + room.getConfig().getGracePeriodMs();

    if (now > graceCutoff) {
        // 真的超时了，Grace Period 也过了
        return ResponseEntity.badRequest().body("出价已截止");
    }

    // Grace Period 内仍接受
    room.recordBid(request.getPlayerId(), request.getAmount());
    return ResponseEntity.ok().build();
}
```

### 10.4 客户端行为

- Grace Period 期间 UI 显示"等待结果..."（转圈）
- 出价按钮在倒计时到 0 时即禁用（客户端不需要知道 Grace Period 的存在）
- 服务端判定完成后，推送 `EVALUATING_DONE` 消息，客户端才更新 UI

---

## 11. 前端设计

### 11.1 页面结构

```
/login          登录 / 注册
/lobby          大厅（查看房间列表、创建房间）
/config         配置页（房主设置 GameConfig）
/room/{roomId}  游戏主界面
/result/{id}    结算页
```

### 11.2 游戏主界面组件

```
RoomLobbyView.vue         准备大厅
  ├─ 角色选择下拉框（6个角色，可重复选择）
  ├─ 道具商店（购买道具消耗金币）
  ├─ 背包展示（已有道具列表）
  └─ ConfigModal.vue       配置弹窗

RoomView.vue              游戏主界面（左右分栏布局）
  ├─ [左侧面板] 玩家信息卡片列表
  │   ├─ 玩家编号、角色名称
  │   ├─ 出价状态（已出价/未出价）
  │   └─ 出价结果（明拍：金额 / 暗拍：排名）
  ├─ [主内容区]
  │   ├─ 技能栏（SKILL_PHASE时显示，手动点击使用技能）
  │   ├─ BiddingPanel.vue       出价面板（倒计时 + 输入框 + 道具选择 + 提交）
  │   ├─ FeedbackBanner.vue     模糊反馈展示
  │   ├─ RevealAnimation.vue    开箱动画
  │   └─ 结算面板
  └─ WarehouseView.vue      仓库物品列表（含品质颜色和价值）

game.js (Pinia Store)
  ├─ 游戏状态 state/round/deadlineTs/players
  ├─ 出价相关 bidCount/playerBids/allBids
  ├─ 个人背包 playerItems/playerCoins
  └─ WebSocket 连接/断线重连/出价/广播处理
```

### 11.3 倒计时同步

```javascript
// 重连后，用服务端下发的绝对时间戳计算剩余时间
function startCountdown(deadlineTs) {
  const updateRemaining = () => {
    const remaining = Math.max(0, deadlineTs - Date.now());
    displaySeconds.value = Math.ceil(remaining / 1000);
    if (remaining <= 0) {
      clearInterval(timer);
      disableBidInput();
    }
  };
  const timer = setInterval(updateRemaining, 200);
  updateRemaining();
}
```

---

## 12. 服务器部署

### 12.1 最低配置

| 项目 | 配置 | 说明 |
|------|------|------|
| CPU | 2 核 | 1 核勉强够，2 核更稳定 |
| 内存 | 2 GB | JVM ~400MB + Redis ~100MB + 系统开销 |
| 硬盘 | 20 GB | 系统 + JAR + MySQL 数据 |
| 带宽 | 1 Mbps | 5 人 WebSocket 极低流量 |
| 系统 | Ubuntu 22.04 LTS | |

推荐：腾讯云/阿里云轻量服务器，2 核 2G，约 40-60 元/月。

### 12.2 Nginx 配置

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /var/www/bidking;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://localhost:8080/;
    }

    # WebSocket（关键配置）
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;   # 必须设置！默认 60s 会断连
        proxy_send_timeout 3600s;
    }
}
```

### 12.3 部署流程

```bash
# 1. 打包
mvn clean package -DskipTests

# 2. 上传 JAR
scp target/bidking.jar user@server:/opt/bidking/

# 3. 启动（推荐 systemd）
# /etc/systemd/system/bidking.service
[Unit]
Description=BidKing Game Server

[Service]
WorkingDirectory=/opt/bidking
ExecStart=/usr/bin/java -Xmx512m -jar bidking.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

---

## 13. 开发阶段划分

按优先级从高到低，每个阶段完成后再开始下一个。

### 阶段 1：骨架搭建

- [ ] Spring Boot 项目初始化（pom.xml 依赖）
- [ ] MySQL 建表 SQL
- [ ] Redis 配置
- [ ] WebSocket STOMP 配置类
- [ ] `GameConfig` 实体 + 管理接口（CRUD）
- [ ] 玩家登录 / 注册（简单 JWT）

### 阶段 2：仓库系统

- [ ] `ItemTemplate` 字典表 + 初始数据（至少 50 条物品）
- [ ] `RngManager`：权重随机生成仓库
- [ ] 黑匣子逻辑

### 阶段 3：核心状态机

- [ ] `GameState` 枚举
- [ ] `GameRoom` 实体（含 `GameConfig` 快照）
- [ ] `RoomManager`：房间创建 / 加入 / 状态转移
- [ ] `TimerService`：按房间独立管理倒计时
- [ ] Grace Period 实现

### 阶段 4：博弈核心

- [ ] `BidEvaluator`：暗标汇总 + 速胜判定 + 平局检测
- [ ] `InfoBroker`：角色权限过滤 + 模糊反馈生成
- [ ] `SkillEngine`：6 个角色技能执行逻辑
- [ ] `RevealEngine`：开箱排序 + 价值结算 + 积分更新

### 阶段 5：WebSocket 通信层

- [ ] 客户端→服务端：出价 / 技能 / 道具指令处理
- [ ] 服务端→客户端：广播 + 私信推送
- [ ] 断线重连接口（`/app/reconnect`）
- [ ] 出价防重（Redis `SETNX` 锁）

### 阶段 6：前端

- [ ] 登录 / 大厅 / 配置页
- [ ] 游戏主界面（WebSocket 状态同步）
- [ ] 倒计时（基于绝对时间戳）
- [ ] 开箱动画
- [ ] 结算页

---

## 14. 道具系统

### 14.1 道具定义（ItemType 枚举）

| 道具 | 价格 | 效果 |
|------|------|------|
| DOUBLE_BID | 2000 | 本轮出价翻倍计算 |
| BID_INSURANCE | 1500 | 未中标返还出价金币 |
| PEEK_TOTAL | 1000 | 查看仓库总价值 |
| EXTRA_3000 | 3000 | 出价额外+3000 |
| HALF_DISCOUNT | 2500 | 中标价减半（截胡卡） |

### 14.2 道具获取

- 准备阶段在房间大厅商店购买，消耗初始金币
- 道具存入 Redis `game:player:state:{roomId}:{playerId}` 的 `items` 字段（JSON 数组）

### 14.3 出价时使用道具

- 玩家在 BiddingPanel 选择道具下拉框，与出价一同提交
- 每次出价限用一个道具
- 道具效果在服务端 `applyItemEffect()` 处理：
  - DOUBLE_BID: `amount *= 2`
  - EXTRA_3000: `amount += 3000`
  - BID_INSURANCE: 存入 `insurance:round:N` 标记，未中标时退款
  - HALF_DISCOUNT: 存入 `halfDiscount:round:N` 标记，中标价减半
  - PEEK_TOTAL: 私信 `/user/queue/item-info` 告知仓库总价值

---

## 15. 技能系统（当前实现）

### 15.1 手动使用

- 角色技能不再自动执行，改为 SKILL_PHASE 期间手动触发
- 玩家通过 `POST /api/room/{roomId}/skill`（REST 接口）使用技能
- 前端 RoomView.vue 在 SKILL_PHASE 时显示小技能栏，包含"使用技能"按钮
- 技能结果直接在 HTTP 响应中返回，不走 WebSocket 推送
- 每个玩家每回合限用一次技能

### 15.2 技能触发建议

| 角色 | 建议使用时机 |
|------|------------|
| LAOTOU（老头） | 首回合（仅首回合有意义） |
| ISABELLA（伊莎贝拉） | 首回合（仅首回合有意义） |
| 其他角色 | 每回合均可使用 |

### 15.3 技能结果格式

```
LAOTOU    → { highQualityCount: N }
AISHA     → { warehouseSize: N }
ETHAN     → { warehouseValue: N } 或 { warehouseValueMin: N, warehouseValueMax: N }
OILMAN    → { avgValue: N }
SOHAI     → { topQuality: "...", topName: "..." }
ISABELLA  → { topQuality: "...", topItemCount: N, topMaxValue: N }
```

---

## 16. 左侧玩家面板（游戏主界面）

### 16.1 功能

- 游戏主界面采用左右分栏布局
- 左侧面板显示所有玩家的实时状态卡片
- 每个卡片包含：玩家编号、角色名称、出价状态

### 16.2 出价状态变化流程

```
① 玩家提交出价 → BID_PLACED 广播 → 该玩家卡片变为"已出价"
② 所有玩家出价完毕 → EVALUATING → ALL_BIDS 广播
③ 明拍模式（blindBidding=false）：显示实际出价金额
④ 暗拍模式（blindBidding=true）：显示排名（第1/2/3名）
```

### 16.3 断线重连恢复

- 重连快照中包含 `bidderIds: string[]`，用于恢复左侧面板的已出价状态
- 玩家角色信息从 `GET /api/room/{roomId}` 获取

---

## 附录：关键设计决策速查

| 问题 | 决策 |
|------|------|
| 倒计时到 0 不立即判定 | 服务端进入 Grace Period（3s），才开始 BidEvaluator |
| 客户端时间不可信 | 以服务端接收时间为准；倒计时用绝对时间戳 `roundDeadline` |
| 断线期间 | 游戏继续，未出价=弃权（值为 -1），不等人 |
| 页面刷新重连 | 下发房间快照，客户端用 `deadlineTs - Date.now()` 恢复倒计时 |
| 已出价断线重连 | 静默恢复，不弹出价框 |
| 未出价断线重连 | 立即推送出价弹窗 |
| 配置生效时机 | 创建房间时快照，中途改配置不影响进行中的局 |
| 防作弊 | 所有比价逻辑在服务端，前端只收模糊反馈 |
| 5 人局倍率 | 推荐 [1.8, 1.5, 1.3, 1.15]，低于官方 4 人局 |
| 状态机驱动 | 只有 `RoomManager` 可改状态，其他 Service 不得直接修改 |
| 定时器 | `ScheduledExecutorService`，按房间独立线程，不用 `@Scheduled` |
| 角色选择 | 准备阶段大厅选择，允许重复选择同一角色 |
| 技能触发 | 手动（REST POST），每回合限一次，不再自动执行 |
| 技能推送 | 不走 WS，HTTP 响应直接返回结果 |
| 出价道具 | 出价时可选一个道具，与服务端 bid 请求一同提交 |
| 道具商店 | 准备阶段在房间大厅购买，消耗金币 |
| 左侧面板 | 游戏主界面左右分栏，显示各玩家出价状态和结果 |
| 明拍/暗拍 | `blindBidding` 配置控制：false 显示金额，true 显示排名 |
| ALL_BIDS 时机 | EVALUATING 开始时广播所有出价汇总信息 |
| BID_PLACED 时机 | 每次出价成功时广播哪位玩家出价了 |
