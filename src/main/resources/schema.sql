CREATE DATABASE IF NOT EXISTS bidking DEFAULT CHARACTER SET utf8mb4;
USE bidking;

-- ── 玩家账号表 ──────────────────────────────────────────────
CREATE TABLE player (
                        id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '玩家ID',
                        username    VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
                        nickname    VARCHAR(50) NOT NULL COMMENT '显示昵称',
                        password    VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
                        total_coins BIGINT DEFAULT 0 COMMENT '历史累计金币',
                        total_score INT DEFAULT 0 COMMENT '历史累计积分',
                        created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
);

-- ── 全局游戏配置表（只有一行，id固定为1）──────────────────────
-- 创建房间时做快照，中途修改不影响进行中的局
CREATE TABLE game_config (
                             id                  INT PRIMARY KEY DEFAULT 1,
                             player_count        INT DEFAULT 5 COMMENT '玩家人数 2~8',
                             initial_coins       BIGINT DEFAULT 10000 COMMENT '每人初始金币',
                             min_bid             BIGINT DEFAULT 1 COMMENT '最低出价下限',
                             speed_win_rounds    INT DEFAULT 4 COMMENT '速胜窗口轮数',
                             final_rounds        INT DEFAULT 1 COMMENT '决战轮数',
                             tie_break_rounds    INT DEFAULT 3 COMMENT '平局加赛最大轮数',
                             skill_phase_secs    INT DEFAULT 20 COMMENT '技能阶段时间（秒）',
                             bid_time_secs       INT DEFAULT 30 COMMENT '出价倒计时（秒）',
                             grace_period_ms     INT DEFAULT 3000 COMMENT '出价冗余时间（毫秒），倒计时到0后再等此时间才判定',
                             speed_win_ratios    JSON NOT NULL DEFAULT (JSON_ARRAY(1.8, 1.5, 1.3, 1.15)) COMMENT '速胜倍率数组，长度须等于speed_win_rounds',
                             warehouse_size_min  INT DEFAULT 10 COMMENT '仓库最少格数',
                             warehouse_size_max  INT DEFAULT 20 COMMENT '仓库最多格数',
                             black_box_enabled   TINYINT DEFAULT 1 COMMENT '是否启用黑匣子',
                             reveal_delay_secs   INT DEFAULT 2 COMMENT '开箱每件物品揭晓延迟（秒）',
                             quality_weights     JSON NOT NULL DEFAULT (JSON_OBJECT('WHITE',40,'GREEN',25,'BLUE',18,'PURPLE',10,'GOLD',5,'RED',2)) COMMENT '品质权重，影响随机生成概率',
                             blind_bidding       TINYINT DEFAULT 1 COMMENT '1=暗标（看不到他人出价），0=明标（可看到他人每轮出价）',
                             fuzzy_feedback      TINYINT DEFAULT 1 COMMENT '1=模糊反馈（领先/靠后），0=精确反馈（显示具体金额）',
                             total_rounds        INT DEFAULT 10 COMMENT '拍卖轮次：每局总轮数，替代速胜+决战轮数'
);

-- 插入默认配置
INSERT INTO game_config (id) VALUES (1) ON DUPLICATE KEY UPDATE id=1;

-- ── 物品字典表 ──────────────────────────────────────────────
-- RngManager 根据品质权重从此表随机抽取物品，value为固定价值
CREATE TABLE item_template (
                               id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '物品模板ID',
                               name         VARCHAR(100) NOT NULL COMMENT '物品名称（中文）',
                               category     ENUM('FURNITURE','DIGITAL','ANTIQUE','BOOK','JEWELRY','FOOD','ELECTRONICS','ART','MUSICAL','WEAPON') NOT NULL COMMENT '品类：家居家具/数码科技/古董珍玩/古典书籍/珠宝宝石/珍稀食材/电子器件/艺术品/乐器/兵器',
                               quality      ENUM('WHITE','GREEN','BLUE','PURPLE','GOLD','RED') NOT NULL COMMENT '品质等级(白→绿→蓝→紫→金→红，品质递增)',
                               value        BIGINT NOT NULL COMMENT '实际价值（固定值）',
                               grid_width   INT DEFAULT 1 COMMENT '仓库网格占用宽度（格数）',
                               grid_height  INT DEFAULT 1 COMMENT '仓库网格占用高度（格数）',
                               grid_size    VARCHAR(5) DEFAULT '1x1' COMMENT '网格占用尺寸 WxH 格式（如 2x2、1x1），用于前端显示',
                               is_black_box TINYINT DEFAULT 0 COMMENT '是否为黑匣子',
                               description  TEXT COMMENT '物品描述',
                               image_url    VARCHAR(255) COMMENT '物品图片URL'
);

-- ── 游戏记录表（每局一行）──────────────────────────────────
CREATE TABLE game_record (
                             id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
                             room_id         VARCHAR(36) NOT NULL COMMENT '房间UUID',
                             config_snapshot JSON NOT NULL COMMENT '本局GameConfig快照',
                             winner_id       BIGINT COMMENT '获胜玩家ID',
                             final_bid       BIGINT COMMENT '最终中标价',
                             warehouse_value BIGINT COMMENT '仓库物品总实际价值',
                             profit          BIGINT COMMENT '获胜者利润 = warehouse_value - final_bid',
                             total_rounds    INT COMMENT '本局总轮次',
                             started_at      DATETIME COMMENT '游戏开始时间',
                             ended_at        DATETIME COMMENT '游戏结束时间'
);

-- ── 玩家游戏参与记录（每局每人一行）────────────────────────
CREATE TABLE game_player_record (
                                    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
                                    game_record_id  BIGINT NOT NULL COMMENT '关联game_record.id',
                                    player_id       BIGINT NOT NULL COMMENT '关联player.id',
                                    character_type  VARCHAR(50) COMMENT '本局使用的角色',
                                    final_bid       BIGINT DEFAULT 0 COMMENT '最终出价',
                                    coins_spent     BIGINT DEFAULT 0 COMMENT '花费金币（道具等）',
                                    profit          BIGINT DEFAULT 0 COMMENT '本局利润',
                                    `rank` INT COMMENT '本局排名'
);
