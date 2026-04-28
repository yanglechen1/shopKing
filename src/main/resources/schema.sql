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
                             warehouse_size_min  INT DEFAULT 20 COMMENT '仓库最少格数',
                             warehouse_size_max  INT DEFAULT 100 COMMENT '仓库最多格数',
                             black_box_enabled   TINYINT DEFAULT 1 COMMENT '是否启用黑匣子',
                             reveal_delay_secs   INT DEFAULT 2 COMMENT '开箱每件物品揭晓延迟（秒）',
                             quality_weights     JSON NOT NULL DEFAULT (JSON_OBJECT('WHITE',40,'GREEN',25,'BLUE',18,'PURPLE',10,'GOLD',5,'RED',2)) COMMENT '品质权重，影响随机生成概率',
                             blind_bidding       TINYINT DEFAULT 1 COMMENT '1=暗标（看不到他人出价），0=明标（可看到他人每轮出价）',
                             fuzzy_feedback      TINYINT DEFAULT 1 COMMENT '1=模糊反馈（领先/靠后），0=精确反馈（显示具体金额）',
                             total_rounds        INT DEFAULT 10 COMMENT '拍卖轮次：每局总轮数，替代速胜+决战轮数',
                             warehouse_theme     VARCHAR(50) DEFAULT 'RANDOM' COMMENT '仓库主题: Category枚举名/UNKNOWN/RANDOM',
                             warehouse_region    VARCHAR(50) DEFAULT 'RANDOM' COMMENT '仓库地区: Region key/RANDOM',
                             weight_deviation    INT DEFAULT 10 COMMENT '品质权重偏移百分比(±)，仓库生成时各品质数量随机波动范围'
);

-- 插入默认配置
INSERT INTO game_config (id) VALUES (1) ON DUPLICATE KEY UPDATE id=1;

-- ── 仓库地区预设表（可热更新品质权重和数量范围）──────────────────
CREATE TABLE region_preset (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    region_key      VARCHAR(50) NOT NULL UNIQUE COMMENT '地区标识，如 DELIVERY_STATION',
    name            VARCHAR(50) NOT NULL COMMENT '显示名称，如 快递站',
    item_count_min  INT NOT NULL DEFAULT 5 COMMENT '最少物品数',
    item_count_max  INT NOT NULL DEFAULT 10 COMMENT '最多物品数',
    weight          INT NOT NULL DEFAULT 10 COMMENT '随机权重（RANDOM模式下选中的概率）',
    quality_weights JSON NOT NULL COMMENT '品质权重: {"WHITE":N,"GREEN":N,...}',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='仓库地区预设，RngManager 从此表读取品质权重和数量范围';

INSERT INTO region_preset (region_key, name, item_count_min, item_count_max, weight, quality_weights) VALUES
('DELIVERY_STATION', '快递站', 5, 10, 30, '{"WHITE":45,"GREEN":25,"BLUE":15,"PURPLE":8,"GOLD":5,"RED":2}'),
('VILLA', '别墅', 10, 18, 40, '{"WHITE":35,"GREEN":28,"BLUE":18,"PURPLE":12,"GOLD":5,"RED":2}'),
('MUSEUM', '博物馆', 15, 25, 20, '{"WHITE":8,"GREEN":12,"BLUE":18,"PURPLE":28,"GOLD":22,"RED":12}'),
('SHIPWRECK', '沉船', 18, 30, 10, '{"WHITE":10,"GREEN":15,"BLUE":20,"PURPLE":25,"GOLD":20,"RED":10}');

-- ── 仓库主题预设表（可热更新品类权重）───────────────────────────
CREATE TABLE theme_preset (
    id               INT PRIMARY KEY AUTO_INCREMENT,
    theme_key        VARCHAR(50) NOT NULL UNIQUE COMMENT '主题标识，如 FURNITURE',
    name             VARCHAR(50) NOT NULL COMMENT '显示名称，如 家具天堂',
    category_weights JSON NOT NULL COMMENT '品类权重: {"FURNITURE":N,"DIGITAL":N,...}',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='仓库主题预设，RngManager 从此表读取品类权重';

INSERT INTO theme_preset (theme_key, name, category_weights) VALUES
('FURNITURE',   '家具天堂', '{"FURNITURE":40,"DIGITAL":7,"ANTIQUE":7,"BOOK":7,"JEWELRY":7,"FOOD":6,"ELECTRONICS":6,"ART":7,"MUSICAL":6,"WEAPON":7}'),
('DIGITAL',     '数码实验室', '{"FURNITURE":7,"DIGITAL":40,"ANTIQUE":7,"BOOK":7,"JEWELRY":7,"FOOD":6,"ELECTRONICS":6,"ART":7,"MUSICAL":6,"WEAPON":7}'),
('ANTIQUE',     '古董地窖', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":40,"BOOK":7,"JEWELRY":7,"FOOD":6,"ELECTRONICS":6,"ART":7,"MUSICAL":6,"WEAPON":7}'),
('BOOK',        '古典书房', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":7,"BOOK":40,"JEWELRY":7,"FOOD":6,"ELECTRONICS":6,"ART":7,"MUSICAL":6,"WEAPON":7}'),
('JEWELRY',     '珠宝金库', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":7,"BOOK":7,"JEWELRY":40,"FOOD":6,"ELECTRONICS":6,"ART":7,"MUSICAL":6,"WEAPON":7}'),
('FOOD',        '珍馐盛宴', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":7,"BOOK":7,"JEWELRY":7,"FOOD":40,"ELECTRONICS":6,"ART":7,"MUSICAL":6,"WEAPON":6}'),
('ELECTRONICS', '电子工坊', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":7,"BOOK":7,"JEWELRY":7,"FOOD":6,"ELECTRONICS":40,"ART":7,"MUSICAL":6,"WEAPON":6}'),
('ART',         '艺术画廊', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":7,"BOOK":7,"JEWELRY":7,"FOOD":6,"ELECTRONICS":6,"ART":40,"MUSICAL":6,"WEAPON":7}'),
('MUSICAL',     '乐器行', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":7,"BOOK":7,"JEWELRY":7,"FOOD":6,"ELECTRONICS":6,"ART":7,"MUSICAL":40,"WEAPON":6}'),
('WEAPON',      '兵器库', '{"FURNITURE":7,"DIGITAL":7,"ANTIQUE":7,"BOOK":7,"JEWELRY":7,"FOOD":6,"ELECTRONICS":6,"ART":7,"MUSICAL":6,"WEAPON":40}'),
('UNKNOWN',     '未知盲盒', '{"FURNITURE":10,"DIGITAL":10,"ANTIQUE":10,"BOOK":10,"JEWELRY":10,"FOOD":10,"ELECTRONICS":10,"ART":10,"MUSICAL":10,"WEAPON":10}');

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
