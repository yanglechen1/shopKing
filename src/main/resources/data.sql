-- 物品字典初始数据（90条）
-- 品质：WHITE(白色) → GREEN(绿色) → BLUE(蓝色) → PURPLE(紫色) → GOLD(金黄色) → RED(红色)
-- 网格尺寸：1x1 ~ 5x5
-- ---------------------------------------------------------------

-- ── 家居家具 FURNITURE ─────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('旧木椅', 'FURNITURE', 'WHITE', 100, 1, 1, '1x1', '普通的旧木椅，略有磨损'),
('棉麻抱枕', 'FURNITURE', 'WHITE', 55, 1, 1, '1x1', '普通棉麻材质抱枕'),
('铁艺台灯', 'FURNITURE', 'WHITE', 140, 1, 1, '1x1', '简单铁艺台灯'),
('竹编收纳篮', 'FURNITURE', 'WHITE', 70, 1, 1, '1x1', '手工竹编收纳篮'),
('实木书架', 'FURNITURE', 'GREEN', 450, 2, 1, '2x1', '榆木实木书架，五层'),
('黄铜落地灯', 'FURNITURE', 'GREEN', 600, 1, 2, '1x2', '复古黄铜落地灯'),
('红木茶几', 'FURNITURE', 'BLUE', 2250, 2, 2, '2x2', '缅甸花梨木茶几'),
('波斯手工地毯', 'FURNITURE', 'BLUE', 3500, 2, 1, '2x1', '伊朗手工编织波斯地毯'),
('明式圈椅', 'FURNITURE', 'PURPLE', 11500, 2, 2, '2x2', '明代风格黄花梨圈椅复刻'),
('乾隆御用屏风', 'FURNITURE', 'GOLD', 70000, 3, 2, '3x2', '清乾隆年间宫廷屏风'),
('紫檀龙椅', 'FURNITURE', 'RED', 550000, 2, 2, '2x2', '清代紫檀木龙椅'),
('黄花梨大床', 'FURNITURE', 'RED', 420000, 4, 3, '4x3', '明代黄花梨架子床');

-- ── 数码科技 DIGITAL ───────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('旧款耳机', 'DIGITAL', 'WHITE', 85, 1, 1, '1x1', '二手有线耳机，音质一般'),
('USB集线器', 'DIGITAL', 'WHITE', 55, 1, 1, '1x1', '4口USB2.0集线器'),
('老款鼠标', 'DIGITAL', 'WHITE', 40, 1, 1, '1x1', '有线光学鼠标'),
('充电宝', 'DIGITAL', 'WHITE', 130, 1, 1, '1x1', '10000mAh普通充电宝'),
('机械键盘', 'DIGITAL', 'GREEN', 450, 2, 1, '2x1', '青轴机械键盘'),
('无线蓝牙耳机', 'DIGITAL', 'GREEN', 600, 1, 1, '1x1', '主动降噪蓝牙耳机'),
('平板电脑', 'DIGITAL', 'BLUE', 3750, 2, 1, '2x1', '10寸高清平板'),
('单反相机', 'DIGITAL', 'BLUE', 4500, 2, 2, '2x2', '入门级单反相机套机'),
('高端显卡', 'DIGITAL', 'PURPLE', 8500, 1, 1, '1x1', '旗舰级独立显卡'),
('专业相机', 'DIGITAL', 'PURPLE', 15000, 2, 2, '2x2', '全画幅专业相机'),
('限量版游戏主机', 'DIGITAL', 'GOLD', 22500, 2, 1, '2x1', '限量联名款游戏主机'),
('初代苹果电脑', 'DIGITAL', 'RED', 200000, 2, 1, '2x1', 'Apple I 初代苹果电脑原机');

-- ── 古董珍玩 ANTIQUE ───────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('民国铜钱', 'ANTIQUE', 'WHITE', 65, 1, 1, '1x1', '民国时期普通铜钱'),
('旧版邮票', 'ANTIQUE', 'WHITE', 100, 1, 1, '1x1', '上世纪普通邮票'),
('老式算盘', 'ANTIQUE', 'WHITE', 140, 2, 1, '2x1', '木质老式算盘'),
('搪瓷茶缸', 'ANTIQUE', 'WHITE', 80, 1, 1, '1x1', '五六十年代搪瓷茶缸'),
('民国瓷碗', 'ANTIQUE', 'GREEN', 550, 1, 1, '1x1', '民国时期青花瓷碗'),
('老式座钟', 'ANTIQUE', 'GREEN', 850, 1, 2, '1x2', '上世纪机械座钟'),
('清代鼻烟壶', 'ANTIQUE', 'BLUE', 3500, 1, 1, '1x1', '清代内画鼻烟壶'),
('民国字画', 'ANTIQUE', 'BLUE', 5500, 2, 1, '2x1', '民国名家书法作品'),
('宋代瓷器', 'ANTIQUE', 'PURPLE', 17500, 1, 2, '1x2', '宋代官窑青瓷'),
('明代铜炉', 'ANTIQUE', 'PURPLE', 14000, 2, 2, '2x2', '明代宣德铜炉'),
('唐三彩马', 'ANTIQUE', 'GOLD', 55000, 2, 2, '2x2', '唐代三彩陶马'),
('汉代玉璧', 'ANTIQUE', 'GOLD', 85000, 2, 2, '2x2', '汉代和田玉璧'),
('商代青铜鼎', 'ANTIQUE', 'RED', 350000, 3, 2, '3x2', '商代晚期青铜礼器');

-- ── 古典书籍 BOOK ───────────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('旧课本', 'BOOK', 'WHITE', 50, 1, 1, '1x1', '民国时期旧课本'),
('民间话本', 'BOOK', 'WHITE', 80, 1, 1, '1x1', '民间流传话本小说'),
('古籍善本', 'BOOK', 'GREEN', 800, 1, 1, '1x1', '明代善本古籍'),
('名家字帖', 'BOOK', 'GREEN', 650, 1, 1, '1x1', '清代名家字帖'),
('宋代刻本', 'BOOK', 'BLUE', 4200, 1, 1, '1x1', '宋代雕版印刷刻本'),
('永乐大典残卷', 'BOOK', 'PURPLE', 18500, 2, 2, '2x2', '永乐大典残存卷册'),
('敦煌佛经手卷', 'BOOK', 'GOLD', 65000, 2, 2, '2x2', '敦煌出土佛经手抄卷'),
('四库全书真本', 'BOOK', 'GOLD', 78000, 3, 3, '3x3', '四库全书珍本原册'),
('竹简兵书', 'BOOK', 'RED', 220000, 3, 2, '3x2', '战国竹简兵书残卷');

-- ── 珠宝宝石 JEWELRY ───────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('玛瑙串珠', 'JEWELRY', 'WHITE', 120, 1, 1, '1x1', '普通玛瑙串珠手链'),
('银戒指', 'JEWELRY', 'WHITE', 90, 1, 1, '1x1', '纯银素面戒指'),
('翡翠吊坠', 'JEWELRY', 'GREEN', 750, 1, 1, '1x1', '冰种翡翠小吊坠'),
('红宝石胸针', 'JEWELRY', 'BLUE', 4800, 1, 1, '1x1', '鸽血红宝石胸针'),
('珍珠项链', 'JEWELRY', 'BLUE', 3200, 1, 1, '1x1', '天然海水珍珠项链'),
('钻石戒指', 'JEWELRY', 'PURPLE', 16000, 1, 1, '1x1', '一克拉钻石铂金戒'),
('翡翠玉佛', 'JEWELRY', 'GOLD', 58000, 1, 1, '1x1', '帝王绿翡翠玉佛摆件'),
('夜明珠', 'JEWELRY', 'RED', 280000, 1, 1, '1x1', '夜光宝珠，鸡蛋大小'),
('皇冠宝石', 'JEWELRY', 'RED', 450000, 2, 2, '2x2', '王室冠镶巨型宝石');

-- ── 珍稀食材 FOOD ───────────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('陈年大米', 'FOOD', 'WHITE', 30, 1, 1, '1x1', '存放三年的陈米'),
('手工挂面', 'FOOD', 'WHITE', 45, 1, 1, '1x1', '农家手工制作挂面'),
('老字号酱油', 'FOOD', 'WHITE', 65, 1, 1, '1x1', '百年老字号酱油一坛'),
('陈年花雕', 'FOOD', 'GREEN', 350, 1, 2, '1x2', '二十年陈绍兴花雕'),
('顶级松露', 'FOOD', 'BLUE', 2800, 1, 1, '1x1', '法国黑松露'),
('野生干鲍', 'FOOD', 'BLUE', 3500, 1, 1, '1x1', '三头南非干鲍'),
('百年普洱茶饼', 'FOOD', 'PURPLE', 12000, 1, 1, '1x1', '百年古树普洱茶饼'),
('宫廷御酒', 'FOOD', 'GOLD', 42000, 2, 1, '2x1', '清宫御制滋补药酒'),
('千年野山参', 'FOOD', 'RED', 320000, 1, 2, '1x2', '千年野生人参，须根完整');

-- ── 电子器件 ELECTRONICS ──────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('电子表', 'ELECTRONICS', 'WHITE', 30, 1, 1, '1x1', '普通电子计算器'),
('计算器', 'ELECTRONICS', 'WHITE', 45, 1, 1, '1x1', '太阳能计算器'),
('老式收音机', 'ELECTRONICS', 'GREEN', 280, 1, 1, '1x1', '电子管老式收音机'),
('老式电视机', 'ELECTRONICS', 'GREEN', 500, 3, 2, '3x2', '显像管老式电视机'),
('功放音响', 'ELECTRONICS', 'BLUE', 3200, 2, 2, '2x2', '专业级功放音响系统'),
('投影仪', 'ELECTRONICS', 'BLUE', 4200, 2, 1, '2x1', '高清投影仪'),
('服务器主板', 'ELECTRONICS', 'PURPLE', 13000, 2, 2, '2x2', '企业级服务器主板'),
('全息投影仪', 'ELECTRONICS', 'GOLD', 85000, 3, 3, '3x3', '新型全息投影设备'),
('量子通信器', 'ELECTRONICS', 'RED', 380000, 2, 2, '2x2', '量子加密通信原型机');

-- ── 艺术品 ART ──────────────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('水彩画', 'ART', 'WHITE', 75, 1, 1, '1x1', '业余画家水彩作品'),
('摄影作品', 'ART', 'WHITE', 60, 1, 1, '1x1', '风景摄影作品'),
('版画', 'ART', 'GREEN', 450, 1, 1, '1x1', '限量编号版画'),
('油画', 'ART', 'BLUE', 3800, 2, 2, '2x2', '写实风格油画'),
('大理石雕塑', 'ART', 'BLUE', 5500, 3, 2, '3x2', '汉白玉人物雕塑'),
('青铜雕像', 'ART', 'PURPLE', 15000, 2, 3, '2x3', '名家青铜人物雕像'),
('梵高向日葵仿作', 'ART', 'PURPLE', 18000, 2, 2, '2x2', '梵高向日葵高仿作品'),
('名家油画', 'ART', 'GOLD', 75000, 3, 2, '3x2', '近现代名家油画真迹'),
('飞天壁画', 'ART', 'RED', 300000, 4, 3, '4x3', '敦煌风格飞天壁画');

-- ── 乐器 MUSICAL ──────────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('口琴', 'MUSICAL', 'WHITE', 40, 1, 1, '1x1', '普通口琴'),
('竹笛', 'MUSICAL', 'WHITE', 60, 1, 1, '1x1', '手工竹笛'),
('二胡', 'MUSICAL', 'GREEN', 380, 1, 2, '1x2', '红木二胡'),
('琵琶', 'MUSICAL', 'BLUE', 3200, 1, 2, '1x2', '花梨木琵琶'),
('古筝', 'MUSICAL', 'BLUE', 4500, 2, 2, '2x2', '桐木古筝'),
('小提琴', 'MUSICAL', 'PURPLE', 12000, 1, 2, '1x2', '意大利手工小提琴'),
('千年古琴', 'MUSICAL', 'RED', 250000, 2, 2, '2x2', '唐代古琴，传世之音'),
('施坦威钢琴', 'MUSICAL', 'GOLD', 120000, 3, 4, '3x4', '施坦威三角钢琴'),
('管风琴', 'MUSICAL', 'GOLD', 150000, 4, 5, '4x5', '教堂大型管风琴');

-- ── 兵器 WEAPON ──────────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, description) VALUES
('铁匕首', 'WEAPON', 'WHITE', 50, 1, 1, '1x1', '普通铁质匕首'),
('木弓', 'WEAPON', 'WHITE', 80, 2, 1, '2x1', '简易木弓'),
('青铜剑', 'WEAPON', 'GREEN', 600, 2, 2, '2x2', '战国青铜剑'),
('长枪', 'WEAPON', 'GREEN', 750, 1, 3, '1x3', '白蜡杆长枪'),
('陌刀', 'WEAPON', 'BLUE', 3500, 2, 3, '2x3', '唐代长柄陌刀'),
('大马士革刀', 'WEAPON', 'PURPLE', 14000, 1, 2, '1x2', '大马士革钢花纹刃'),
('龙泉宝剑', 'WEAPON', 'GOLD', 55000, 1, 3, '1x3', '龙泉古法锻造宝剑'),
('轩辕剑仿品', 'WEAPON', 'GOLD', 72000, 2, 3, '2x3', '轩辕剑高仿工艺品'),
('方天画戟', 'WEAPON', 'RED', 280000, 2, 4, '2x4', '精钢方天画戟');

-- ── 黑匣子（特殊物品）──────────────────────────────────────
INSERT INTO item_template (name, category, quality, value, grid_width, grid_height, grid_size, is_black_box, description) VALUES
('神秘黑匣子', 'FURNITURE', 'WHITE', 200000, 2, 2, '2x2', 1, '开启后随机，可能是巨额宝物，也可能是废铁');
