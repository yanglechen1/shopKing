# BidKing 技术参考（AI 速查用）

> **维护规则**：每次修改代码后，必须检查本文件是否需要同步更新。以下场景必须更新本文档：
> - 新增/删除/重命名 API 端点或字段
> - 修改状态机流转逻辑
> - 修改 Redis Key 设计或 TTL
> - 修改 WS 消息类型或 payload 结构
> - 修改道具/技能效果逻辑
> - 修改配置项或限流规则
> - 发现并修复 bug 后，更新相关章节反映修复后的行为

## 项目结构

```
shopKing/
├── src/main/java/com/bidking/
│   ├── annotation/
│   │   └── RateLimit.java              # 自定义限流注解 @RateLimit("ruleKey")
│   ├── config/
│   │   ├── RateLimitAspect.java        # AOP切面(@Around拦截带@RateLimit的Controller方法)
│   │   ├── RateLimitProperties.java    # 映射 application.yml → rate-limit.rules
│   │   ├── GlobalExceptionHandler.java # 全局异常(含429 → Retry-After:60)
│   │   ├── WebConfig.java              # CORS + 静态资源
│   │   ├── SecurityConfig.java         # Spring Security(禁用大部分, 仅留JWT)
│   │   └── WebSocketConfig.java        # STOMP + SockJS端点 /ws
│   ├── controller/
│   │   ├── RoomController.java         # REST: 房间/角色/技能/准备/踢人(全量@RateLimit)
│   │   ├── GameWsController.java       # WS: 出价/道具使用/断线重连(手动 tryAcquire 限流)
│   │   ├── AuthController.java         # 登录/注册(@RateLimit)
│   │   └── GameConfigController.java   # 全局配置CRUD(@RateLimit)
│   ├── exception/
│   │   └── RateLimitException.java     # 限流异常(status=429)
│   ├── service/
│   │   ├── RoomManager.java            # 状态机核心(唯一写GameState的地方)
│   │   ├── BidEvaluator.java           # 出价汇总+速胜判定
│   │   ├── InfoBroker.java             # 模糊反馈生成
│   │   ├── RngManager.java             # 仓库随机生成(权重配置，10列网格)
│   │   ├── SkillEngine.java            # 6角色探查技能
│   │   ├── TimerService.java           # 每轮倒计时(ScheduledExecutorService)
│   │   ├── RevealEngine.java           # 开箱+结算
│   │   ├── RateLimitService.java       # Redis INCR+EXPIRE 滑动窗口
│   │   └── GameConfigService.java      # 全局配置数据库持久化
│   ├── dto/
│   │   ├── GameRoom.java               # 房间状态(Redis JSON, 含 pendingPurchases)
│   │   ├── BidRequest.java             # 出价请求(roomId, amount, clientTs)
│   │   └── UseItemRequest.java         # 道具使用请求(roomId, itemType)
│   ├── enums/
│   │   ├── GameState.java              # 状态枚举
│   │   ├── Quality.java                # 品质枚举(WHITE→GREEN→BLUE→PURPLE→GOLD→RED)
│   │   ├── Category.java               # 品类枚举(10种)
│   │   └── ItemType.java               # 道具枚举(仅做类型标识, 价格/名称在前端)
│   ├── entity/
│   │   ├── GameConfig.java             # 可配置参数表(MyBatis-Plus)
│   │   └── ItemTemplate.java           # 物品字典表(含 gridSize 字段)
│   └── filter/
│       └── JwtFilter.java              # JWT 鉴权(OncePerRequestFilter)
├── frontend/src/
│   ├── views/
│   │   ├── RoomLobbyView.vue           # 准备大厅(选角色+本地商店+准备)
│   │   └── RoomView.vue                # 游戏主界面(左=玩家信息，中=局内信息，右=仓库网格，底=技能+道具+出价)
│   ├── components/
│   │   ├── FeedbackBanner.vue          # 反馈展示
│   │   ├── RevealAnimation.vue         # 开箱动画
│   │   └── ConfigModal.vue             # 配置弹窗
│   └── stores/
│       └── game.js                     # Pinia: WS连接+游戏状态(含itemResults, bidSubmitted)
└── BidKing_PRD.md                      # 完整游戏规则文档
```

---

## 状态机

```
WAITING ──startGame()──→ BIDDING ──timer──→ GRACE_PERIOD ──timer──→ EVALUATING
  ↑                                                                      │
  │                                                            ┌─────────┼─────────┐
  │                                                            ▼         ▼         ▼
  │                                                      SPEED_WIN    TIE_BREAK   NO_WIN
  │                                                          │           │          │
  │                                                          ▼           ▼          │
  │                                                   REVEALING ──→ SETTLING        │
  │                                                          │                    │
  │                                                          ▼                    │
  │                                                      FINISHED ←──────────────┘
  └────────────────────────────────────────────────────────────────────────────────┘
```

**注意：**
- SKILL_PHASE 已移除，技能和道具全部在 BIDDING 阶段使用
- 技能由玩家在 BIDDING 阶段手动触发 REST 端点，道具通过 WS 使用

---

## 后端核心类速查

### RoomManager — 状态机核心
| 方法 | 作用 |
|------|------|
| `createRoom(hostId)` | 创建房间(UUID)，hostId 设为房主；自动清理旧房间(含非空旧房+房主转移) |
| `joinRoom(roomId, playerId)` | 加入(锁防竞态 + 一人一房：自动离开旧WAITING房) |
| `selectCharacter(roomId, playerId, type)` | 选角色(允许重复选择) |
| `setReady(roomId, playerId, purchases)` | 准备(校验房间成员 + 校验 purchases 总额 ≤ initialCoins，存入 pendingPurchases) |
| `cancelReady(roomId, playerId)` | 取消准备(清理该玩家的 pendingPurchases) |
| `startGame(roomId, requesterId)` | 开始游戏(生成仓库 → initPlayerStates → startNewRound → BIDDING) |
| `initPlayerStates(room)` | 从 pendingPurchases 扣金币、道具写入 Redis，清空 pendingPurchases |
| `startNewRound(roomId)` | 进入下一轮(round++ → 清理pendingBuff → 检查最大轮次 → enterBidding) |
| `enterBidding(roomId)` | 进入出价阶段(重置所有玩家的 hasBidThisRound) |
| `triggerEvaluation(roomId)` | 判定(广播 ALL_BIDS → evaluate → 保险/截胡处理 → 反馈) |
| `applyItemEffect(roomId, pid, itemType, amount)` | 计算道具效果(×2 / +3000 / 保险标记 / 截胡标记 / 透视推送) |
| `useItem(roomId, pid, itemType)` | 独立使用道具（独立于出价，通过WS调用）从背包移除→存pendingBuff/效果→返回结果；每轮限一个buff类道具防覆盖浪费 |
| `consumePendingBuff(roomId, pid, amount)` | 出价时消耗 pendingBuff(翻倍/加价)，返回修正后金额 |
| `kickPlayer(roomId, hostId, targetId)` | 房主踢人(清理目标全部房间数据 + 解绑 player:room + 广播) |
| `broadcast(roomId, type, payload)` | 统一广播封装 `{type, payload}` |

### GameWsController — WebSocket
| 方法 | 作用 |
|------|------|
| `submitBid(BidRequest, Principal)` | 出价(SETNX防重 → 消耗pendingBuff(道具效果) → BID_PLACED广播 + 全部出完提前判定；手动限流) |
| `useItem(UseItemRequest, Principal)` | **独立使用道具**（先使用道具，结果返回到 `/user/queue/item-result`，之后再出价） |
| `reconnect(payload, Principal)` | 断线重连(返回 state/round/deadlineTs/bidderIds/coins/items；手动限流) |

> 出价和道具使用已完全分离：先通过 `/app/room/useItem` 使用道具获得结果，再通过 `/app/room/bid` 出价。
> 道具效果（翻倍/加价）存入 pendingBuff，出价时自动消耗。

### RoomController — REST 端点
| 端点 | 限流规则 | 说明 |
|------|---------|------|
| `POST /api/room/create` | createRoom | 创建房间 |
| `POST /api/room/{roomId}/join` | joinRoom | 加入房间 |
| `POST /api/room/{roomId}/start` | startGame | 开始游戏(房主) |
| `GET /api/room/{roomId}` | getRoom | 查房间(warehouse已脱敏移除value/playerIds/playerCharacters) |
| `POST /api/room/{roomId}/character?type=X` | selectCharacter | 选角色 |
| `POST /api/room/{roomId}/skill?category=X` | useSkill | 手动使用技能(SETNX防重, category可选) |
| `POST /api/room/{roomId}/ready` | ready | 准备(body: `{purchases:["DOUBLE_BID",...]}`) |
| `POST /api/room/{roomId}/unready` | ready | 取消准备 |
| `PUT /api/room/{roomId}/config` | updateConfig | 更新配置(房主) |
| `POST /api/room/{roomId}/kick/{targetId}` | kickPlayer | 房主踢人 |

---

## 5 种道具

道具数据硬编码在 `RoomLobbyView.vue` 的 `SHOP_ITEMS` 数组，后端 `ItemType.java` 仅做类型标识。

购买流程：**前端本地**扣 `localCoins` → 加入 `localPurchases[]` → 点"准备"提交 `purchases` 数组 → `setReady()` 校验总额 → `initPlayerStates()` 统一扣金币写 Redis。

| 枚举值 | 价格 | 效果 | 实现 |
|--------|------|------|------|
| DOUBLE_BID | 2000 | 出价×2 | `useItem` → 存 pendingBuff → `consumePendingBuff` → `amount *= 2` |
| EXTRA_3000 | 3000 | 出价+3000 | `useItem` → 存 pendingBuff → `consumePendingBuff` → `amount += 3000` |
| BID_INSURANCE | 1500 | 未中标退钱 | 设保险标记, `triggerEvaluation` 退 |
| HALF_DISCOUNT | 2500 | 中标价减半 | 设截胡标记, `triggerEvaluation` 除2 |
| PEEK_TOTAL | 1000 | 看仓库总价值 | `useItem` → 推 `/user/queue/item-info` |

Redis: `game:player:state:{roomId}:{pid}` → `items` (JSON 字符串数组)

---

## 6 角色技能 (SkillEngine.java)

均为物品探查，**不暴露具体价值**，只揭示名称/品质/品类/数量。

| 角色 | 方法 | 返回 | 效果 |
|------|------|------|------|
| LAOTOU | `laotouSkill` | `{type:PEEK_RANDOM, name, quality, category}` | 随机窥探一件物品 |
| AISHA | `aishaSkill` | `{type:CATEGORY_SCAN, category, count, items[{name,quality}]}` | 扫描指定品类所有物品(需传 category，支持10品类) |
| ETHAN | `ethanSkill` | `{type:TOP_OUTLINE, quality, category}` | 最高品质物品的轮廓(品类+品质) |
| OILMAN | `oilmanSkill` | `{type:QUALITY_DISTRIBUTION, distribution:{WHITE:N,...}}` | 各品质等级数量分布 |
| SOHAI | `sohaiSkill` | `{type:DEEP_PEEK, name, quality, category, valueTier, isBlackBox}` | 随机一件完整信息+模糊价值等级 |
| ISABELLA | `isabellaSkill` | `{type:TOP_ITEMS, quality, count, sampleName}` | 最高品质名称+数量 |

---

## WS 消息完整目录

### 服务端 → 全体 `/topic/room/{roomId}` (广播)

| type | 触发时机 | payload | 前端处理 |
|------|---------|---------|---------|
| `PLAYER_JOINED` | 加入/踢人 | `{playerId, playerCount, kicked?}` | 刷新 playerIds |
| `CHARACTER_UPDATE` | 选角色 | `{playerCharacters}` | 更新角色映射 |
| `READY_UPDATE` | 准备/取消 | `{readyPlayerIds, playerIds}` | 更新准备集合 |
| `ROUND_START` | 出价阶段开始（也是每轮的第一个事件） | `{round, deadlineTs}` | state=BIDDING, 重置 playerBids |
| `BID_PLACED` | 有人出价 | `{playerId, count, total}` | `playerBids[playerId]=true` |
| `ALL_BIDS` | EVALUATING 开始 | `{bids/ranking, items:{pid:itemType}}` | `allBids = msg.payload`, 累积 roundHistory |
| `TIE_BREAK` | 平局加赛 | `{tieBreakCount}` | state=TIE_BREAK |
| `REVEALING_START` | 开箱开始 | `{}` | state=REVEALING |
| `GAME_SETTLED` | 结算完成 | `{winnerId, finalBid, ...}` | `settled = msg` |
| `GAME_FINISHED` | 游戏结束 | `{}` | 保留结算状态 |
| `GAME_FORCE_END` | 超轮数强制结束 | `{round, reason}` | state=FINISHED |

### 服务端 → 个人 `/user/queue/*`

| queue | 内容 | 说明 |
|-------|------|------|
| `/user/queue/feedback` | `{type, round, feedback/highestBid}` | 出价反馈(模糊/精确) |
| `/user/queue/reconnect` | `{action, state, round, deadlineTs, hasBidThisRound, bidderIds, coins, items, pendingBuff}` | 重连快照(含背包恢复+待激活增益) |
| `/user/queue/error` | `{error}` | 错误消息(含限流拒绝) |
| `/user/queue/item-info` | `{type:'PEEK_TOTAL', totalValue}` | 透视卡结果 |
| `/user/queue/item-result` | `{itemType, success, message}` | **道具使用结果**（独立使用道具后返回） |
| `/user/queue/bid-ack` | `{round, amount}` | 出价确认 |

---

## 一局游戏的完整数据流

```
1. 大厅阶段
   前端: 选角色 → 本地购买道具扣 coins → 点"准备"
   POST /api/room/{roomId}/ready (body: {purchases:[...]}) → setReady()
      └─ 后端校验总额 ≤ initialCoins, 存入 room.pendingPurchases
   POST /api/room/{roomId}/start → startGame()
      ├─ RngManager.generate(config)        → 生成仓库（10列网格，紧凑填充）
      ├─ initPlayerStates(room)             → 读取pendingPurchases→扣金币写Redis→清除pending
      └─ startNewRound(roomId)              → 第1轮，直接进入 BIDDING

2. 出价阶段 (BIDDING)
   ROUND_START 广播 → 底部出价栏（技能、道具、出价均在当前阶段使用）

   技能使用 (REST):
   POST /api/room/{roomId}/skill?category=X → useSkill() (手动, SETNX防重)

   **道具使用**（独立于出价，先使用再出价）:
   WS /app/room/useItem → useItem()
      ├─ 从背包移除道具
      ├─ 存入 pendingBuff 或保险/截胡标记
      └─ 结果 → /user/queue/item-result（前端显示在中间局内信息面板）

   提交出价:
   WS /app/room/bid → submitBid()
      ├─ SETNX 防重占位
      ├─ consumePendingBuff() (消耗道具增益)
      ├─ 记录本轮道具使用 → game:items:{roomId}:{round}
      └─ BID_PLACED 广播 (含count/total)

3. Grace Period (GRACE_PERIOD)
   timer到期 → triggerEvaluation()

4. 判定 (EVALUATING)
   triggerEvaluation()
      ├─ ALL_BIDS 广播
      ├─ BidEvaluator.evaluate() → 速胜/平局/继续(NO_WIN→startNewRound下一轮)
      ├─ 保险退款 + 截胡减半
      └─ 模糊反馈 → /user/queue/feedback

5. 开箱 (REVEALING) + 结算
   RevealEngine.reveal() → 按品质从低到高逐件揭晓
   finishGame() → FINISHED
```

---

## Redis Key 设计

| Key | 类型 | 示例 | 说明 | TTL |
|-----|------|------|------|-----|
| `game:room:{roomId}` | String(JSON) | `{state, currentRound, playerIds, warehouse, ...}` | 房间完整状态 | 2h |
| `game:bids:{roomId}:{round}` | Hash | `{pid1:"5000", pid2:"-1"}` | 出价(-1=弃权) | 24h |
| `game:player:room:{playerId}` | String | `roomId` | 玩家→房间映射 | 2h |
| `game:player:state:{roomId}:{pid}` | Hash | `{character, coins, items[], hasBidThisRound, skillUsed, pendingBuff, insurance:round:N, halfDiscount:round:N}` | 玩家状态(含待处理增益+保险/截胡标记) | 2h |
| `game:items:{roomId}:{round}` | Hash | `{pid1: "DOUBLE_BID", pid2: "EXTRA_3000"}` | 各轮道具使用 | 24h |
| `rate:limit:{action}:{identity}:{window}` | String(计数) | INCR 值 | 限流滑动窗口 | 60s |

---

## 仓库系统

### 网格布局
- 固定 **10列**网格
- 物品从左到右、从上到下**紧凑填充**，中间无空位
- 物品尺寸：1×1 到 5×5，编码为 `grid_size` 列（如 "2x2"、"3x4"）
- 前端显示 `gridSize`（WxH 格式）在每个物品格子中

### 生成逻辑（Theme × Region 双维模式）
`RngManager.generate()`：
1. 从 `warehouse_themes` JSON 解析主题列表，按 `warehouseTheme` 字段（"RANDOM"=随机选）
2. 从 `warehouse_regions` JSON 解析地区列表，按 `warehouseRegion` 字段（"RANDOM"=随机选）
3. Region 决定物品数量范围（itemCountMin~Max）和品质权重（qualityWeights）
4. Theme 决定品类权重（categoryWeights），主导品类 40%，其余均分
5. 逐个生成：quality = weightedRandom(region.qualityWeights), category = weightedRandom(theme.categoryWeights)
6. `randomByQualityAndCategory(quality, category)` 精确命中 → fallback `randomByQuality(quality)`
7. 30%概率追加黑匣子
8. `assignGridPositions()` 分配网格位置（10列紧凑排列）
- **回退**：`warehouseRegions`/`warehouseThemes` 为空时，退回旧模式（warehouseSizeMin/Max + qualityWeights）

### Theme（仓库主题）→ 决定品类分布
| Key | 名称 | 主品类(40%) |
|-----|------|-----------|
| FURNITURE | 家具天堂 | FURNITURE |
| DIGITAL | 数码实验室 | DIGITAL |
| ANTIQUE | 古董地窖 | ANTIQUE |
| BOOK | 古典书房 | BOOK |
| JEWELRY | 珠宝金库 | JEWELRY |
| FOOD | 珍馐盛宴 | FOOD |
| ELECTRONICS | 电子工坊 | ELECTRONICS |
| ART | 艺术画廊 | ART |
| MUSICAL | 乐器行 | MUSICAL |
| WEAPON | 兵器库 | WEAPON |
| UNKNOWN | 未知盲盒 | 10品类各10%均匀 |

### Region（仓库地区）→ 决定大小 + 品质
| Key | 名称 | 物品数 | 品质权重(白/绿/蓝/紫/金/红) |
|-----|------|--------|--------------------------|
| DELIVERY_STATION | 快递站 | 5~10 | 45/25/15/8/5/2 |
| VILLA | 别墅 | 10~18 | 35/28/18/12/5/2 |
| MUSEUM | 博物馆 | 15~25 | 8/12/18/28/22/12 |
| SHIPWRECK | 沉船 | 18~30 | 10/15/20/25/20/10 |

### GameConfig 新增字段
| 字段 | 类型 | 说明 |
|------|------|------|
| `warehouseTheme` | VARCHAR | 主题 key / "RANDOM" |
| `warehouseRegion` | VARCHAR | 地区 key / "RANDOM" |
| `warehouseRegions` | JSON | Region 预设列表（可热更新品质权重和数量范围） |
| `warehouseThemes` | JSON | Theme 预设列表（可热更新品类权重） |

### 物品品质（6级）
| 品质 | 颜色 | 权重(默认) |
|------|------|-----------|
| WHITE | 白色 | 40 |
| GREEN | 绿色 | 25 |
| BLUE | 蓝色 | 18 |
| PURPLE | 紫色 | 10 |
| GOLD | 金黄色 | 5 |
| RED | 红色 | 2 |

### 品类（10种）
FURNITURE(家居家具)、DIGITAL(数码科技)、ANTIQUE(古董珍玩)、BOOK(古典书籍)、
JEWELRY(珠宝宝石)、FOOD(珍稀食材)、ELECTRONICS(电子器件)、ART(艺术品)、
MUSICAL(乐器)、WEAPON(兵器)

### 物品字典表 `item_template`
含 `grid_size` VARCHAR(5) 列，格式 "WxH"（如 "2x2"、"3x4"），用于前端显示网格占用尺寸。
保留 `grid_width` 和 `grid_height` 用于后端网格位置计算。

---

## 关键实现细节

### 道具与出价分离
道具使用和出价是两个独立的 WS 端点：
- **使用道具**: `/app/room/useItem` → 从背包移除 → 存入 `pendingBuff`（翻倍/加价）或标记（保险/截胡/透视）→ 结果推送 `/user/queue/item-result`
- **提交出价**: `/app/room/bid` → SETNX 防重 → 消耗 `pendingBuff` → 计算最终金额 → 广播
- 中间局内信息面板展示最近使用的道具及其结果

### 出价防重
`SETNX game:bids:{roomId}:{round} {pid} "0"` 占位，再 `opsForHash().put()` 覆盖实际金额（道具可修改金额）。`0` 是占位符，在重连中会被过滤（不算"已出价"）。

### Grace Period
倒计时归零后等 `gracePeriodMs`(3s)：期间出价仍接收，超过拒收。

### 保险卡退款
`triggerEvaluation()` 遍历有保险标记的玩家，未中标退还出价金额。

### 截胡卡
胜者标记 `halfDiscount:round:N` → `finalHighestBid /= 2`。

### 断线重连
- `/app/reconnect` 返回快照 + `bidderIds` 数组(过滤掉占位符"0")
- 前端恢复 `playerBids` 映射、游戏状态、背包 coins/items
- 未出价玩家显示"未出价"

### 仓库生成
`startGame()` 时 `RngManager.generate(config)` 按权重随机生成。仅服务端 Redis 持有，前端 `GET /api/room/{roomId}` 获取。

### 游戏重新开始
游戏结束后（FINISHED），房主点击"再来一局" → `POST /api/room/{roomId}/restart`：
- 重置 `currentRound=0`、`state=WAITING`、清空 warehouse、tieBreakCount
- 删除所有玩家的 Redis 状态（`game:player:state:{roomId}:{pid}`）
- 删除所有轮次的出价和道具记录（`game:bids:{roomId}:{r}`、`game:items:{roomId}:{r}`）
- 清空 `readyPlayerIds`、`pendingPurchases`、`playerCharacters`
- 广播 `GAME_RESTART` → 前端导航到 `/room/{roomId}/lobby`
- 房主再次准备+开始

### 拍卖轮次配置
`GameConfig.totalRounds`（默认 10）替代 `speedWinRounds + finalRounds`。`startNewRound()` 优先使用 `totalRounds`，为 null 时回退旧逻辑。

### 限流系统
三层防护：

1. **@RateLimit 注解(REST)** — `RateLimitAspect` 拦截所有带注解的 Controller 方法，INCR+EXPIRE 滑动窗口(Key: `rate:limit:{action}:{identity}:{window}`)。已认证用户按 userId，未认证按 IP。超限 → `RateLimitException` → 返回 429 + Retry-After:60。

2. **RateLimitService.tryAcquire() (WS)** — `submitBid()` 和 `reconnect()` 内手动调用，超限推 `/user/queue/error`。

3. **Redis 降级** — Redis 连接失败时放行(fail-open)。

配置 `application.yml` → `rate-limit.rules` (带中文注释)：

| 规则 | 限制 |
|------|------|
| login / register | 5次/60s, 3次/60s |
| createRoom / joinRoom | 3次/60s, 10次/60s |
| startGame / ready / getRoom / selectCharacter / useSkill | 30次/60s 为主 |
| submitBid (WS) / reconnect (WS) / useItem (WS) | 5次/60s, 5次/60s, 10次/60s |
| kickPlayer / updateConfig / updateGlobalConfig | 5次/60s |
| getGlobalConfig | 10次/60s |

### 一人一房
`joinRoom()` 先用 `game:player:room:{playerId}` 查是否已在其他房间，若旧房间是 WAITING 状态则自动离开(从 playerIds 移除 + 解绑映射)，若旧房间已开始则拒绝加入。

### 房主踢人
`kickPlayer()`：校验房主身份 → 目标在房间内 → 从 `playerIds/readyPlayerIds/playerCharacters/pendingPurchases` 移除 → 删除 `game:player:room:{targetId}` → 广播 `PLAYER_JOINED`(含 `kicked:true`)。

---

## 最近修改摘要 (2026-04-27)

### 品质和品类系统重构
- **品质改为6级**：WHITE(白色) → GREEN(绿色) → BLUE(蓝色) → PURPLE(紫色) → GOLD(金黄色) → RED(红色)，删除旧 COMMON/ADVANCED/RARE/EPIC/LEGEND/MYTH
- **品类扩展为10种**：FURNITURE(家居家具)、DIGITAL(数码科技)、ANTIQUE(古董珍玩)、BOOK(古典书籍)、JEWELRY(珠宝宝石)、FOOD(珍稀食材)、ELECTRONICS(电子器件)、ART(艺术品)、MUSICAL(乐器)、WEAPON(兵器)
- **网格尺寸扩展**：1×1 ~ 5×5 自由组合，新增 `grid_size` 列(WxH格式)
- **物品总数扩充至90件**，每种品质在各品类中均有分布
- 更新 `Quality.java`、`Category.java` 枚举，更新 `QualityEnum`、`SkillEngine.qualityOrder()`、`RevealEngine.QUALITY_ORDER`
- 前端品质颜色样式更新（白/绿/蓝/紫/金/红边框色）

### grid_size 列新增
- `item_template` 表新增 `grid_size VARCHAR(5)` 列，格式 "WxH"（如 "2x2"、"3x4"）
- `ItemTemplate.java` 新增 `gridSize` 字段
- `RngManager.buildItem()` 将 gridSize 写入物品 Map
- 前端仓库网格每个格子显示 gridSize 信息

### 道具与出价分离
- 新增独立 WS 端点 `/app/room/useItem`，道具使用与出价完全解耦
- 道具使用结果推送到 `/user/queue/item-result`，前端展示在中间局内信息面板
- `submitBid()` 通过 `consumePendingBuff()` 消耗道具效果，不直接处理道具
- 新增 `UseItemRequest.java` DTO

### 界面布局：三栏 → 四栏
- 左侧：玩家信息面板
- **中间新增：局内信息面板**（展示最近使用的道具及其结果信息）
- 右侧：仓库10列网格面板（显示 gridSize、品质边框色）
- 底部：技能区 + 道具区 + 出价区

### 其他修改
- `GameWsController` 新增 `useItem()` 方法，处理 `/app/room/useItem`
- 默认品质权重更新：`{"WHITE":40,"GREEN":25,"BLUE":18,"PURPLE":10,"GOLD":5,"RED":2}`
- 修复：data.sql 中 `旧版邮票` 条目 SQL 拼接错误
- `target/classes/` 下的 SQL 文件也需要同步更新

---

## 最近修改摘要 (2026-04-27) 续

### 取消技能阶段，技能/道具合并到出价阶段
- **状态机简化**：移除 SKILL_PHASE 状态，`startGame()` 直接进入 BIDDING
- **新方法 `startNewRound()`**：替代 `enterSkillPhase()`，负责轮次递增/pendingBuff清理/最大轮次检查后直接调 `enterBidding()`
- **技能使用放松**：`RoomController.useSkill()` 状态检查从 `!= SKILL_PHASE` 改为 `== WAITING || == FINISHED`，技能可在 BIDDING 阶段使用
- **技能阶段倒计时移除**：`TimerService.startSkillTimer()` 删除，`skillPhaseSecs` 配置不再使用
- **前端的SKILL_PHASE引用全部清理**：`game.js` 删除 `SKILL_PHASE_START` 广播处理；`RoomLobbyView.vue` 导航触发器改为 `ROUND_START`；`RoomView.vue` 移除 `isPlaying/stateLabel/input禁用` 中所有 SKILL_PHASE 引用
- **配置面板**：`ConfigModal.vue` / `ConfigView.vue` 删除 `skillPhaseSecs` 输入项

---

## 最近修改摘要 (2026-04-27) Bug 修复

### 仓库价值泄露修复
- `GET /api/room/{roomId}` 返回的 `warehouse` 中每件物品的 `value` 字段已被移除，防止玩家通过 REST API 直接查看所有物品实际价值
- 新增 `RoomController.sanitizeRoom()` 方法，返回脱敏后的房间数据

### 创建房间幽灵玩家修复
- `createRoom()` 中旧房间清理逻辑扩展：房主离开非空旧房间时移除自身、转移房主给下一个玩家
- 修复前：房主在有其他玩家的旧房间中创建新房间时，旧房间保留幽灵玩家

### 道具覆盖防护
- `useItem()` 中新增检查：DOUBLE_BID / EXTRA_3000 使用前检查 `pendingBuff` 是否已存在，防止第二个 buff 覆盖第一个导致道具浪费
- 检查放在 `removePlayerItem()` 之前，校验失败不消耗道具

### 平局加赛道具恢复
- `enterTieBreak()` 中从 `game:items:{roomId}:{round}` 读取本轮已使用 buff 道具，恢复 `pendingBuff` 到各玩家状态
- 修复前：加赛中 `pendingBuff` 在首次出价时已被 `consumePendingBuff()` 删除，玩家道具白白消耗

### 重连恢复 pendingBuff
- `reconnect()` 响应新增 `pendingBuff` 字段，断线重连后前端可恢复待激活增益的 UI 提示
- 前端 `game.js` 在重连快照中读取 `pendingBuff` 并写入 `itemResults` 面板

### 其他修复
- 玩家状态 Redis Key (`game:player:state:{roomId}:{pid}`) 新增 2h TTL，防止内存泄露
- `useItem` WS 端点新增限流规则：10次/60s
- 移除 `BidRequest.itemType` 死字段（道具已独立于出价）
- 移除 `SkillEngine.useSkill()` 未使用的 `round` 参数
- **多轮出价修复**：`bidSubmitted` 移入 Pinia store，在 `ROUND_START` 广播处理器中直接复位；移除组件级 round watcher，消除 Pinia 响应式传递失效导致第二轮无法出价的问题

---

## 最近修改摘要 (2026-04-27) Theme × Region 仓库生成重构

### 仓库生成升级为 Theme × Region 双维模式
- **Region（地区）决定仓库大小 + 品质分布**：快递站(5~10件/偏白绿)、别墅(10~18件/均匀)、博物馆(15~25件/偏紫金)、沉船(18~30件/偏红金)
- **Theme（主题）决定品类分布**：10个品类各对应一个主题(主品类40%) + UNKNOWN 未知盲盒(均匀)
- `GameConfig` 新增 4 个字段：`warehouseTheme`、`warehouseRegion`、`warehouseRegions`(JSON)、`warehouseThemes`(JSON)
- Region/Theme 预设值存储在 JSON 列中，可通过 `PUT /api/config` 热更新品质权重和品类权重
- `RngManager.generate()` 重构：先选 Region+Theme → 品质×品类双维加权 → `randomByQualityAndCategory()` 精确命中
- `ItemTemplateMapper` 新增 `randomByQualityAndCategory(quality, category)` 方法
- 新增 `WarehouseRegion.java`、`WarehouseTheme.java` DTO
- 未配置 JSON 列时自动回退旧模式（warehouseSizeMin/Max + qualityWeights）