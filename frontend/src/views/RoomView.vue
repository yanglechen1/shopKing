<template>
  <div class="room">
    <div class="header">
      <span>房间: {{ roomId }}</span>
      <span v-if="themeName || regionName" class="header-theme">{{ themeName }} / {{ regionName }}</span>
      <span v-if="game.round > 0">第 {{ game.round }} / {{ totalRounds }} 轮</span>
      <span>状态: {{ stateLabel }}</span>
    </div>

    <!-- 游戏主体：左玩家 + 右仓库 -->
    <div class="game-layout">
      <!-- 左侧玩家信息面板 -->
      <div class="left-panel">
        <h3>玩家信息</h3>
        <div v-for="(pid, idx) in playerList" :key="pid" class="player-card">
          <div class="pc-top">
            <span class="pc-name">玩家 {{ idx + 1 }}</span>
            <span class="pc-char">{{ getCharName(playerChars[pid]) }}</span>
          </div>
          <div class="pc-rounds">
            <div v-for="(slot, ri) in roundSlots" :key="ri" class="pc-round-entry">
              <div class="pc-round-box"
                   :class="{ 'pc-cur-box': slot.isCurrent, 'pc-future-box': slot.isFuture }"
                   :title="slot.isComplete ? itemTooltip(slot.history.items, pid) : ''">
                <span v-if="slot.isComplete && slot.history.items && slot.history.items[pid]" class="pc-box-item">{{ itemShort(slot.history.items[pid]) }}</span>
                <span v-else-if="slot.isCurrent && (game.state === 'BIDDING' || game.state === 'GRACE_PERIOD')">...</span>
              </div>
              <div class="pc-round-label">
                <template v-if="slot.isComplete">
                  <span v-if="blindBidding && slot.history.ranking != null" class="pc-rank-val">{{ slot.history.ranking.indexOf(pid) + 1 }}</span>
                  <span v-else-if="!blindBidding && slot.history.bids && Number(slot.history.bids[pid]) >= 0" class="pc-bid-val">{{ formatBid(Number(slot.history.bids[pid])) }}</span>
                  <span v-else class="pc-pass-val">弃权</span>
                </template>
                <span v-else-if="slot.isCurrent && (game.state === 'BIDDING' || game.state === 'GRACE_PERIOD')" :class="'pc-cur ' + (hasBid(pid) ? 'cur-yes' : 'cur-no')">
                  {{ hasBid(pid) ? '已出价' : '未出价' }}
                </span>
                <span v-else class="pc-pass-val">-</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="playerList.length === 0" class="pc-empty">等待玩家数据...</div>
      </div>

      <!-- 中间局内信息面板 -->
      <div class="mid-panel">
        <h3>局内信息</h3>
        <div v-if="game.itemResults && game.itemResults.length > 0" class="mid-list">
          <div v-for="(ir, i) in game.itemResults" :key="i" class="mid-item">
            <span class="mid-icon">{{ getItemName(ir.itemType) }}</span>
            <span class="mid-msg">{{ ir.message }}</span>
          </div>
        </div>
        <div v-else class="mid-empty">暂无信息</div>
      </div>

      <!-- 右侧仓库面板 -->
      <div class="right-panel" v-if="warehouse.length > 0">
        <h3>仓库 <span class="wh-info">{{ warehouse.length }}件 / 10列</span></h3>
        <div class="wh-grid" :style="{ gridTemplateColumns: 'repeat(10, 1fr)' }">
          <div v-for="(item, i) in warehouse" :key="i"
               class="wh-cell"
               :class="[(item.quality || '').toLowerCase(), item.isBlackBox ? 'is-black' : '']"
               :style="{
                 gridColumn: (item.gridX != null ? item.gridX + 1 : (i % 10) + 1) + ' / span ' + (item.gridWidth || 1),
                 gridRow: (item.gridY != null ? item.gridY + 1 : Math.floor(i / 10) + 1) + ' / span ' + (item.gridHeight || 1)
               }">
            <span class="wh-cell-name">{{ item.name }}</span>
            <span class="wh-cell-quality">{{ qualityLabel(item.quality) }}</span>
            <span class="wh-cell-size">{{ item.gridSize || item.gridWidth + 'x' + item.gridHeight }}</span>
            <span v-if="item.isBlackBox" class="wh-cell-black">黑匣子</span>
            <span v-if="item.revealed" class="wh-cell-value">{{ item.value }}g</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 技能结果展示 -->
    <div v-if="skillUsedLocal && skillResultLocal" class="skill-result-row">
      <span class="sr-title">技能结果</span>
      <span class="sr-data">{{ formatSkillResult(skillResultLocal) }}</span>
    </div>

    <!-- 反馈横幅 -->
    <div v-if="game.feedback" class="feedback-row">
      <FeedbackBanner :feedback="game.feedback" />
    </div>

    <!-- 开箱动画 -->
    <div v-if="game.state === 'REVEALING'" class="reveal-row">
      <RevealAnimation :messages="game.messages" />
    </div>

    <!-- 底部操作栏（游戏进行中） -->
    <div v-if="isPlaying" class="bottom-bar">
      <!-- 技能区 -->
      <div class="bb-group bb-skill">
        <span class="bb-title">技能</span>
        <div class="bb-body">
          <select v-if="myCharacter === 'AISHA' && !skillUsedLocal" v-model="selectedCategory" class="bb-select">
            <option value="">品类...</option>
            <option value="FURNITURE">家居家具</option>
            <option value="DIGITAL">数码科技</option>
            <option value="ANTIQUE">古董珍玩</option>
            <option value="BOOK">古典书籍</option>
            <option value="JEWELRY">珠宝宝石</option>
            <option value="FOOD">珍稀食材</option>
            <option value="ELECTRONICS">电子器件</option>
            <option value="ART">艺术品</option>
            <option value="MUSICAL">乐器</option>
            <option value="WEAPON">兵器</option>
          </select>
          <button @click="useSkill" :disabled="skillUsedLocal || (myCharacter === 'AISHA' && !selectedCategory)" class="bb-btn">
            {{ skillUsedLocal ? '已使用' : '使用技能' }}
          </button>
          <span v-if="skillErrorLocal" class="bb-err">{{ skillErrorLocal }}</span>
          <span v-else-if="!skillUsedLocal" class="bb-hint">{{ skillHint }}</span>
        </div>
      </div>

      <!-- 道具区（独立使用，不绑定出价） -->
      <div class="bb-group bb-items">
        <span class="bb-title">道具</span>
        <div class="bb-body">
          <template v-if="game.playerItems && game.playerItems.length > 0">
            <button v-for="(it, i) in game.playerItems" :key="i"
                    class="bb-item-card"
                    @click="useItem(it)" :disabled="game.bidSubmitted || itemUsing">
              {{ getItemName(it) }}
            </button>
          </template>
          <span v-else class="bb-empty">无道具</span>
        </div>
      </div>

      <!-- 出价区（道具已独立使用，出价不再关联道具） -->
      <div class="bb-group bb-bid">
        <span class="bb-title">
          出价
          <span class="bb-countdown" v-if="game.deadlineTs">{{ remaining }}s</span>
        </span>
        <div class="bb-body">
          <input v-model.number="bidAmount" type="number" placeholder="金额" class="bb-input"
                 :disabled="game.bidSubmitted" />
          <button @click="submitBid" :disabled="!canBid" class="bb-btn bb-bid-btn">
            {{ game.bidSubmitted ? '已出价' : '提交出价' }}
          </button>
          <span v-if="game.bidSubmitted" class="bb-done">已出价</span>
        </div>
      </div>
    </div>

    <!-- 等待开始 / 结算（非进行中状态） -->
    <div v-if="game.state === null || game.state === 'WAITING'" class="waiting-box">
      <p>等待玩家加入...</p>
      <button @click="startGame">开始游戏（房主）</button>
    </div>

    <div v-if="game.settled" class="settled">
      <h3>游戏结束</h3>
      <p>获胜者: {{ game.settled.winnerId }}</p>
      <p>中标价: {{ game.settled.finalBid }}</p>
      <p>仓库价值: {{ game.settled.warehouseValue }}</p>
      <p>利润: {{ game.settled.profit }}</p>
      <div class="settled-actions">
        <button @click="restartGame" class="restart-btn">再来一局</button>
        <button @click="router.push('/lobby')">返回大厅</button>
      </div>
    </div>

    <!-- 错误消息 -->
    <div v-for="(msg, i) in errorMessages" :key="i" class="err">{{ msg.text }}</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGameStore } from '../stores/game'
import { useAuthStore } from '../stores/auth'
import axios from 'axios'
import FeedbackBanner from '../components/FeedbackBanner.vue'
import RevealAnimation from '../components/RevealAnimation.vue'

const route = useRoute()
const router = useRouter()
const game = useGameStore()
const auth = useAuthStore()
const roomId = route.params.id

// 进入页面时立即复位出价状态，防止上一局 SPA 残留；重连时由 hasBidThisRound watcher 恢复
game.bidSubmitted = false

// Player info for left panel
const playerList = ref([])
const playerChars = ref({})

// Manual skill state
const skillUsedLocal = ref(false)
const skillResultLocal = ref(null)
const skillErrorLocal = ref('')
const selectedCategory = ref('')

// 当前玩家角色
const myCharacter = computed(() => playerChars.value[String(auth.playerId)] || null)

// Warehouse & config
const warehouse = ref([])
const blindBidding = ref(true)
const totalRounds = ref(10)
const themeName = ref('')
const regionName = ref('')

// Bidding state (inline, replaces BiddingPanel)
// bidSubmitted 已移至 store，ROUND_START 时自动复位
const bidAmount = ref(0)
const remaining = ref(0)

// 道具使用状态由 store 统一管理（含服务端响应确认）
const itemUsing = computed(() => game.itemPending)

const CHAR_NAMES = {
  LAOTOU: '老头', AISHA: '艾莎', ETHAN: '伊森',
  OILMAN: '石油哥', SOHAI: '索嗨', ISABELLA: '伊莎贝拉'
}

const qualityNames = {
  WHITE: '白色', GREEN: '绿色', BLUE: '蓝色',
  PURPLE: '紫色', GOLD: '金黄色', RED: '红色'
}

const ITEM_SHORT = {
  DOUBLE_BID: '翻倍', EXTRA_3000: '+3000',
  BID_INSURANCE: '保险', HALF_DISCOUNT: '截胡', PEEK_TOTAL: '透视'
}
const ITEM_DESC = {
  DOUBLE_BID: '翻倍卡：出价×2', EXTRA_3000: '加价券：出价+3000',
  BID_INSURANCE: '保险卡：未中标退钱', HALF_DISCOUNT: '截胡卡：中标价减半',
  PEEK_TOTAL: '透视卡：查看仓库总价值'
}
const itemNames = {
  DOUBLE_BID: '翻倍卡', BID_INSURANCE: '保险卡',
  PEEK_TOTAL: '透视卡', EXTRA_3000: '加价券',
  HALF_DISCOUNT: '截胡卡'
}

function qualityLabel(q) { return qualityNames[q] || q }
function getCharName(type) { return CHAR_NAMES[type] || type || '未选择' }
function getItemName(type) { return itemNames[type] || type }

function hasBid(pid) {
  return game.playerBids && game.playerBids[String(pid)]
}

const roundSlots = computed(() => {
  const current = game.round
  if (current === 0) return []
  const total = Math.max(totalRounds.value, game.roundHistory.length + 1)
  const slots = []
  for (let i = 1; i <= total; i++) {
    const histEntry = game.roundHistory.find(h => h.round === i)
    slots.push({
      round: i,
      isComplete: !!histEntry,
      isCurrent: !histEntry && i === current,
      isFuture: !histEntry && i > current,
      history: histEntry || null
    })
  }
  return slots
})

function itemShort(type) { return ITEM_SHORT[type] || '' }
function itemTooltip(items, pid) {
  const t = items && items[pid]
  return t ? ITEM_DESC[t] || t : ''
}

/** 格式化金额：1000→1k, 1500000→1.5m, 1000000000→1b */
function formatBid(amount) {
  if (amount == null || isNaN(amount)) return '-'
  if (amount >= 1000000000) return (amount / 1000000000).toFixed(1) + 'b'
  if (amount >= 1000000) return (amount / 1000000).toFixed(1) + 'm'
  if (amount >= 1000) return (amount / 1000).toFixed(1) + 'k'
  return String(amount)
}

// 游戏进行中（显示底部操作栏）
const isPlaying = computed(() =>
  game.state === 'BIDDING' || game.state === 'GRACE_PERIOD'
)

// 可否出价（道具使用中不允许出价，防止 pendingBuff 未写入）
const canBid = computed(() =>
  !game.bidSubmitted && !itemUsing.value && bidAmount.value > 0 &&
  (game.state === 'BIDDING' || game.state === 'GRACE_PERIOD')
)

const skillHint = computed(() => {
  if (myCharacter.value === 'AISHA') return '选择品类后使用技能扫描该品类物品'
  if (myCharacter.value === 'LAOTOU') return '随机窥探仓库中的一件物品'
  if (myCharacter.value === 'ETHAN') return '感知最高品质物品的轮廓'
  if (myCharacter.value === 'OILMAN') return '透视各品质等级分布'
  if (myCharacter.value === 'SOHAI') return '深度探查随机物品'
  if (myCharacter.value === 'ISABELLA') return '感知顶级收藏品'
  return '点击使用角色技能'
})

function formatSkillResult(result) {
  if (!result) return ''
  if (result.error) return '技能使用失败: ' + result.error
  switch (result.type) {
    case 'PEEK_RANDOM':
      return '随机窥探到一件物品：' + result.name + '（' + qualityLabel(result.quality) + '，' + categoryLabel(result.category) + '）'
    case 'CATEGORY_SCAN':
      return '【' + categoryLabel(result.category) + '】类共 ' + result.count + ' 件：' +
        (result.items || []).map(i => i.name + '(' + qualityLabel(i.quality) + ')').join('、')
    case 'TOP_OUTLINE':
      return '最高品质物品轮廓：' + qualityLabel(result.quality) + '，属' + categoryLabel(result.category) + '类'
    case 'QUALITY_DISTRIBUTION':
      return '品质分布：' + Object.entries(result.distribution || {})
        .filter(([, v]) => v > 0)
        .map(([q, v]) => qualityLabel(q) + '×' + v).join('、')
    case 'DEEP_PEEK':
      return '深度探查：' + result.name + '（' + qualityLabel(result.quality) + '，' + categoryLabel(result.category) + '）价值: ' + result.valueTier + (result.isBlackBox ? ' [黑匣子]' : '')
    case 'TOP_ITEMS':
      return '顶级感知：最高品质为' + qualityLabel(result.quality) + '，共 ' + result.count + ' 件，包括「' + result.sampleName + '」等'
    default:
      return JSON.stringify(result, null, 1)
  }
}

function categoryLabel(cat) {
  const map = {
    FURNITURE: '家居家具', DIGITAL: '数码科技', ANTIQUE: '古董珍玩',
    BOOK: '古典书籍', JEWELRY: '珠宝宝石', FOOD: '珍稀食材',
    ELECTRONICS: '电子器件', ART: '艺术品', MUSICAL: '乐器', WEAPON: '兵器'
  }
  return map[cat] || cat
}

const stateLabel = computed(() => {
  const map = {
    WAITING: '等待中', BIDDING: '出价中', EVALUATING: '判定中',
    GRACE_PERIOD: '等待结果', TIE_BREAK: '加赛', REVEALING: '开箱中',
    SETTLING: '结算中', FINISHED: '已结束'
  }
  return map[game.state] || game.state
})

const errorMessages = computed(() =>
  game.messages.filter(m => m.type === 'error').slice(-3)
)

// ── 倒计时 ──
let countdownTimer = null
onMounted(() => {
  countdownTimer = setInterval(() => {
    remaining.value = Math.max(0, Math.ceil((game.deadlineTs - Date.now()) / 1000))
  }, 200)
})
onUnmounted(() => clearInterval(countdownTimer))

onMounted(async () => {
  game.connect(roomId)
  await fetchRoomData()
})

/** 拉取房间数据（含仓库） */
async function fetchRoomData() {
  try {
    const res = await axios.get(`/api/room/${roomId}`)
    if (res.data.warehouse && res.data.warehouse.length > 0) {
      warehouse.value = res.data.warehouse
    }
    playerList.value = (res.data.playerIds || []).map(String)
    playerChars.value = res.data.playerCharacters || {}
    blindBidding.value = res.data.config?.blindBidding !== false
    // 主题/地区名称
    const cfg = res.data.config || {}
    totalRounds.value = cfg.totalRounds || 10
    const themeKey = cfg.warehouseTheme
    const regionKey = cfg.warehouseRegion
    const themeOpts = res.data.themeOptions || []
    const regionOpts = res.data.regionOptions || []
    const ft = themeOpts.find(t => t.key === themeKey)
    const fr = regionOpts.find(r => r.key === regionKey)
    themeName.value = ft ? ft.name : themeKey || ''
    regionName.value = fr ? fr.name : regionKey || ''
  } catch (e) {
    console.warn('[RoomView] 获取房间数据失败', e)
  }
}

// 进入 BIDDING 时如果仓库为空则拉取（首次加载或断线重连）
watch(() => game.state, (newState, oldState) => {
  if (newState === 'BIDDING' && oldState !== newState) {
    if (warehouse.value.length === 0) {
      fetchRoomData()
    }
  }
})

// 断线重连后恢复出价状态（store 在 reconnect 快照中更新 hasBidThisRound）
watch(() => game.hasBidThisRound, (val) => {
  if (val) game.bidSubmitted = true
})

onUnmounted(() => {
  game.disconnect()
})

async function startGame() {
  await axios.post(`/api/room/${roomId}/start`)
}

async function restartGame() {
  try {
    await axios.post(`/api/room/${roomId}/restart`)
    router.push(`/room/${roomId}/lobby`)
  } catch (e) {
    console.warn('[RoomView] 重新开始失败', e)
  }
}

async function useSkill() {
  if (skillUsedLocal.value) return
  skillErrorLocal.value = ''
  try {
    const params = {}
    if (selectedCategory.value) params.category = selectedCategory.value
    const res = await axios.post(`/api/room/${roomId}/skill`, null, { params })
    skillResultLocal.value = res.data
    skillUsedLocal.value = true
  } catch (e) {
    console.warn('[RoomView] 技能使用失败', e)
    const data = e.response?.data
    const errMsg = typeof data === 'string' ? data : data?.error || '技能使用失败，请重试'
    skillErrorLocal.value = errMsg
    setTimeout(() => { skillErrorLocal.value = '' }, 3000)
  }
}

function submitBid() {
  if (game.bidSubmitted || bidAmount.value <= 0) return
  if (game.state !== 'BIDDING' && game.state !== 'GRACE_PERIOD') return
  game.submitBid(roomId, bidAmount.value)
}

/** 使用道具（独立于出价，通过 WS 发送，道具移除由 store 在 item-result 确认后执行） */
function useItem(itemType) {
  if (game.bidSubmitted || itemUsing.value) return
  game.useItem(roomId, itemType)
}
</script>

<style scoped>
.room { max-width: 1200px; margin: 20px auto; display: flex; flex-direction: column; gap: 10px; }
.header { display: flex; gap: 20px; font-weight: bold; flex-wrap: wrap; }

/* ── 主体布局：左面板 + 右仓库 ── */
.game-layout { display: flex; gap: 16px; min-height: 280px; }

/* ── 左侧玩家面板 ── */
.left-panel { width: 260px; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; }
.left-panel h3 { margin: 0 0 4px; font-size: 1rem; }
.player-card { background: #1a1a2e; border-radius: 6px; padding: 10px; display: flex; flex-direction: column; gap: 4px; border-left: 3px solid #4a9eff; }
.pc-top { display: flex; justify-content: space-between; align-items: center; }
.pc-name { font-weight: bold; font-size: 0.95rem; }
.pc-char { font-size: 0.8rem; color: #4a9eff; }
.pc-empty { color: #666; font-size: 0.85rem; padding: 8px; }

/* 多轮出价框 */
.pc-rounds { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.pc-round-entry { display: flex; flex-direction: column; align-items: center; gap: 1px; }
.pc-round-box {
  width: 36px; height: 36px; border-radius: 4px; border: 1px solid #4a9eff44;
  display: flex; align-items: center; justify-content: center;
  font-size: 0.65rem; background: #16213e; color: #aaa; cursor: default;
  transition: background 0.15s;
}
.pc-round-box:hover { background: #1a2744; }
.pc-box-item { color: #ffd700; font-size: 0.6rem; line-height: 1.1; text-align: center; }
.pc-cur-box { border-style: dashed; border-color: #4a9eff66; }
.pc-round-label { font-size: 0.65rem; min-height: 1em; text-align: center; }
.pc-rank-val { color: #ff8800; font-weight: bold; font-size: 0.7rem; }
.pc-bid-val { color: #ffd700; font-family: monospace; font-size: 0.65rem; }
.pc-pass-val { color: #555; font-size: 0.6rem; }
.pc-cur { font-size: 0.6rem; }
.cur-yes { color: #7fff7f; }
.cur-no { color: #888; }

/* ── 中间局内信息面板 ── */
.mid-panel {
  width: 200px; flex-shrink: 0; display: flex; flex-direction: column; gap: 6px;
  background: #1a1a2e; border-radius: 8px; padding: 12px;
}
.mid-panel h3 { margin: 0 0 4px; font-size: 0.9rem; }
.mid-list { display: flex; flex-direction: column; gap: 4px; overflow-y: auto; max-height: 240px; }
.mid-item { display: flex; flex-direction: column; gap: 1px; padding: 5px 6px; background: #16213e; border-radius: 4px; border-left: 2px solid #4a9eff; }
.mid-icon { font-size: 0.75rem; color: #4a9eff; font-weight: bold; }
.mid-msg { font-size: 0.75rem; color: #ddd; }
.mid-empty { font-size: 0.75rem; color: #555; padding: 8px 0; }

/* ── 右侧仓库网格面板 ── */
.right-panel {
  flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px;
  background: #1a1a2e; border-radius: 8px; padding: 12px;
}
.right-panel h3 { margin: 0; font-size: 0.95rem; }
.wh-info { font-size: 0.7rem; color: #888; font-weight: normal; }
.wh-grid {
  display: grid; gap: 3px; overflow-y: auto; max-height: 340px;
  grid-auto-rows: minmax(40px, auto);
}
.wh-cell {
  background: #16213e; border-radius: 4px; border: 1px solid #333;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 3px; font-size: 0.65rem; min-height: 36px;
  overflow: hidden; cursor: default;
}
.wh-cell-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
.wh-cell-quality { font-size: 0.6rem; opacity: 0.8; }
.wh-cell-black { font-size: 0.6rem; background: #333; padding: 0 4px; border-radius: 2px; color: #ff8800; }
.wh-cell-value { font-size: 0.6rem; color: #ffd700; }
.wh-cell-size { font-size: 0.55rem; color: #888; font-family: monospace; }

/* 品质边框色（白→绿→蓝→紫→金→红） */
.wh-cell.white  { border-color: #aaaaaa; background: #1a1a2e; }
.wh-cell.green  { border-color: #44dd44; background: #1a2e1a; }
.wh-cell.blue   { border-color: #44aaff; background: #1a253f; }
.wh-cell.purple { border-color: #cc44ff; background: #221a3f; }
.wh-cell.gold   { border-color: #ffaa00; background: #2e1f0f; }
.wh-cell.red    { border-color: #ff3333; background: #2e1510; }
.wh-cell.is-black { border-color: #ff4400; background: #1a0f0f; }

/* ── 技能结果 ── */
.skill-result-row { padding: 10px 14px; background: #16213e; border-radius: 8px; display: flex; gap: 10px; align-items: baseline; }
.sr-title { color: #4a9eff; font-weight: bold; font-size: 0.85rem; white-space: nowrap; }
.sr-data { font-size: 0.85rem; color: #ddd; line-height: 1.5; }

/* ── 反馈 & 开箱 ── */
.feedback-row { }
.reveal-row { }

/* ── 底部操作栏 ── */
.bottom-bar {
  display: flex; gap: 12px; padding: 12px 16px;
  background: #1a1a2e; border-radius: 10px; border: 1px solid #4a9eff33;
  flex-wrap: wrap;
}
.bb-group { display: flex; flex-direction: column; gap: 6px; flex: 1; min-width: 180px; }
.bb-title { font-size: 0.75rem; color: #888; text-transform: uppercase; letter-spacing: 0.5px; display: flex; align-items: center; gap: 8px; }
.bb-body { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.bb-select { padding: 5px 6px; font-size: 0.8rem; border-radius: 4px; background: #16213e; color: #ddd; border: 1px solid #4a9eff66; }
.bb-btn {
  padding: 6px 14px; font-size: 0.8rem; border: none; border-radius: 5px;
  cursor: pointer; font-weight: bold; white-space: nowrap; color: white;
  background: #4a9eff; transition: background 0.15s;
}
.bb-btn:hover { background: #3a8eff; }
.bb-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.bb-input {
  padding: 6px 8px; font-size: 0.9rem; border-radius: 5px;
  background: #16213e; color: #ddd; border: 1px solid #4a9eff66; width: 100px;
}
.bb-countdown { font-size: 1rem; font-weight: bold; color: #e94560; }
.bb-err { font-size: 0.75rem; color: #ff6b6b; }
.bb-hint { font-size: 0.75rem; color: #888; }
.bb-done { color: #7fff7f; font-size: 0.8rem; }
.bb-empty { font-size: 0.75rem; color: #555; }

/* 道具卡片按钮 */
.bb-item-card {
  padding: 4px 10px; font-size: 0.75rem; border-radius: 4px;
  border: 1px solid #4a9eff44; background: #16213e; color: #ddd;
  cursor: pointer; transition: all 0.15s;
}
.bb-item-card:hover { background: #1a2744; border-color: #4a9eff88; }
.bb-item-on { background: #0f3460; border-color: #ffd700; color: #ffd700; }

.bb-bid-btn { background: #e94560; }
.bb-bid-btn:hover { background: #d63850; }

/* ── 等待 / 结算 ── */
.waiting-box { padding: 30px; text-align: center; }
.waiting-box button { padding: 10px 24px; background: #4a9eff; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: bold; }

.settled { padding: 16px; background: #0f3460; border-radius: 8px; }
.settled-actions { display: flex; gap: 10px; margin-top: 12px; }
.restart-btn { padding: 10px 20px; background: #e94560; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: bold; }

.err { color: #ff6b6b; font-size: 0.9rem; }

/* 未开始的轮次灰框 */
.pc-future-box { border-color: #333; background: #111; opacity: 0.35; }
/* 主题/地区显示 */
.header-theme { color: #ffd700; font-size: 0.9rem; }
</style>
