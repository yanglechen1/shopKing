<template>
  <div class="room-lobby">
    <h2>房间准备</h2>
    <div class="room-id">
      房间ID: <strong>{{ roomId }}</strong>
      <button @click="copy">复制</button>
    </div>

    <!-- 玩家列表 + 角色选择 -->
    <div class="players">
      <h3>玩家列表 ({{ players.length }}/{{ cfg.playerCount }})</h3>
      <div v-for="p in players" :key="p" class="player-item">
        <span class="player-name">
          {{ p }}<span v-if="String(p) === String(hostId)"> （房主）</span>
        </span>
        <!-- 自己 → 下拉选角色 -->
        <select v-if="String(p) === myId"
                :value="myCharacter"
                @change="selectCharacter($event.target.value)"
                class="char-select">
          <option value="" disabled>选择角色</option>
          <option v-for="c in ALL_CHARS" :key="c.value" :value="c.value">
            {{ c.label }}
          </option>
        </select>
        <!-- 其他人 → 显示角色 -->
        <span v-else class="char-display">
          {{ getCharName(playerCharacters[String(p)]) || '未选择' }}
        </span>
        <span :class="readySet.has(String(p)) ? 'ready' : 'not-ready'">
          {{ readySet.has(String(p)) ? '已准备' : '未准备' }}
        </span>
        <!-- 房主踢人按钮 -->
        <button v-if="isHost && String(p) !== myId"
                @click="kickPlayer(p)" class="kick-btn">踢出</button>
      </div>
    </div>

    <!-- 道具商店（纯本地操作，不调后端） -->
    <div class="shop" v-if="myCharacter">
      <h3>道具商店 <span class="coins">金币: {{ localCoins }}</span></h3>
      <div class="shop-items">
        <div v-for="item in SHOP_ITEMS" :key="item.type" class="shop-item">
          <div class="si-name">{{ item.name }}</div>
          <div class="si-desc">{{ item.desc }}</div>
          <div class="si-price">{{ item.price }} 金币</div>
          <button @click="buyItem(item.type)"
                  :disabled="localCoins < item.price || iAmReady"
                  class="buy-btn">购买</button>
        </div>
      </div>
      <!-- 本地背包：已购买的道具 -->
      <div class="inventory" v-if="localPurchases.length > 0">
        <h4>我的背包（{{ localPurchases.length }} 件）</h4>
        <span v-for="(it, i) in localPurchases" :key="i" class="item-tag">
          {{ getItemName(it) }}
          <button v-if="!iAmReady" @click="removeItem(i)" class="remove-btn">×</button>
        </span>
      </div>
    </div>

    <!-- 配置弹出框 -->
    <ConfigModal v-if="showConfig" :config="cfg" :editable="isHost"
      :room-id="roomId" @close="showConfig=false" @saved="cfg=$event" />

    <div class="actions">
      <button @click="showConfig=true">查看配置</button>
      <button v-if="!iAmReady && myCharacter" class="ready-btn" @click="ready">准备</button>
      <button v-if="iAmReady" class="cancel-btn" @click="cancelReady">取消准备</button>
      <button v-if="isHost" class="start-btn" :disabled="!allReady" @click="startGame">
        开始游戏{{ allReady ? '' : `（${readySet.size}/${players.length - 1} 已准备）` }}
      </button>
    </div>

    <p class="err" v-if="err">{{ err }}</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import axios from 'axios'
import ConfigModal from '../components/ConfigModal.vue'

/**
 * 道具商店数据（与后端 ItemType.java 枚举一一对应）
 * 纯前端静态数据，购买操作不调后端，准备时一次提交
 */
const SHOP_ITEMS = [
  { type: 'DOUBLE_BID',    name: '翻倍卡',   price: 2000, desc: '出价×2' },
  { type: 'EXTRA_3000',    name: '加价券',   price: 3000, desc: '出价+3000' },
  { type: 'BID_INSURANCE', name: '保险卡',   price: 1500, desc: '未中标退钱' },
  { type: 'HALF_DISCOUNT', name: '截胡卡',   price: 2500, desc: '中标价减半' },
  { type: 'PEEK_TOTAL',    name: '透视卡',   price: 1000, desc: '看仓库总价值' },
]

const ALL_CHARS = [
  { value: 'LAOTOU', label: '老头' },
  { value: 'AISHA', label: '艾莎' },
  { value: 'ETHAN', label: '伊森' },
  { value: 'OILMAN', label: '石油哥' },
  { value: 'SOHAI', label: '索嗨' },
  { value: 'ISABELLA', label: '伊莎贝拉' },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const roomId = route.params.id
const err = ref('')
const showConfig = ref(false)
const players = ref([])
const hostId = ref(null)
const cfg = ref({})
const readySet = ref(new Set())
const playerCharacters = ref({})

// 本地道具购买状态（不调后端，准备时一次提交）
const localCoins = ref(0)
const localPurchases = ref([])

const myId = computed(() => String(auth.playerId))
const isHost = computed(() => myId.value === String(hostId.value))
const iAmReady = computed(() => readySet.value.has(myId.value))
const myCharacter = computed(() => playerCharacters.value[myId.value] || '')

const allReady = computed(() =>
  players.value
    .map(String)
    .filter(p => p !== String(hostId.value))
    .every(p => readySet.value.has(p))
)

function getCharName(type) {
  const found = ALL_CHARS.find(c => c.value === type)
  return found ? found.label : ''
}

function getItemName(type) {
  return SHOP_ITEMS.find(i => i.type === type)?.name || type
}

let client = null

onMounted(async () => {
  const res = await axios.get(`/api/room/${roomId}`)
  hostId.value = res.data.hostId
  players.value = res.data.playerIds
  cfg.value = { ...res.data.config, regionOptions: res.data.regionOptions ?? [], themeOptions: res.data.themeOptions ?? [] }
  readySet.value = new Set((res.data.readyPlayerIds ?? []).map(String))
  playerCharacters.value = res.data.playerCharacters || {}

  // 从配置读取初始金币作为本地余额
  localCoins.value = cfg.value.initialCoins || 0

  client = new Client({
    webSocketFactory: () => new SockJS(`/ws?token=${auth.token}`),
    onConnect: () => {
      client.subscribe(`/topic/room/${roomId}`, ({ body }) => {
        const msg = JSON.parse(body)
        console.log('[WS] 准备室收到广播:', msg)
        if (msg.type === 'PLAYER_JOINED') {
          axios.get(`/api/room/${roomId}`).then(r => {
            players.value = r.data.playerIds
            hostId.value = r.data.hostId
          })
        }
        if (msg.type === 'CHARACTER_UPDATE') {
          playerCharacters.value = msg.payload.playerCharacters || {}
        }
        if (msg.type === 'READY_UPDATE') {
          readySet.value = new Set((msg.payload.readyPlayerIds ?? []).map(String))
          players.value = msg.payload.playerIds
        }
        if (msg.type === 'ROUND_START') {
          router.push(`/room/${roomId}`)
        }
      })
    }
  })
  client.activate()
})

onUnmounted(() => client?.deactivate())

async function selectCharacter(type) {
  try {
    await axios.post(`/api/room/${roomId}/character?type=${type}`)
    playerCharacters.value = { ...playerCharacters.value, [myId.value]: type }
  } catch (e) {
    err.value = e.response?.data || '选择角色失败'
  }
}

/**
 * 购买道具（纯前端操作，只改本地状态）
 * 扣本地金币 → 加入本地背包，不调后端
 */
function buyItem(itemType) {
  const item = SHOP_ITEMS.find(i => i.type === itemType)
  if (!item) return
  if (localCoins.value < item.price) return
  localCoins.value -= item.price
  localPurchases.value = [...localPurchases.value, itemType]
}

/** 从本地背包移除道具（取消购买） */
function removeItem(index) {
  const removed = localPurchases.value[index]
  if (!removed) return
  const item = SHOP_ITEMS.find(i => i.type === removed)
  localCoins.value += item ? item.price : 0
  localPurchases.value = localPurchases.value.filter((_, i) => i !== index)
}

/** 准备：将本地购买的清单一次提交到后端 */
async function ready() {
  try {
    await axios.post(`/api/room/${roomId}/ready`, {
      purchases: localPurchases.value
    })
  } catch (e) {
    err.value = e.response?.data || '准备失败'
  }
}

/** 取消准备（后端会清空该玩家的购买清单） */
async function cancelReady() {
  try {
    await axios.post(`/api/room/${roomId}/unready`)
  } catch (e) {
    err.value = e.response?.data || '取消失败'
  }
}

async function startGame() {
  try {
    await axios.post(`/api/room/${roomId}/start`)
    router.push(`/room/${roomId}`)
  } catch (e) {
    err.value = e.response?.data || '开始失败'
  }
}

/** 房主踢人 */
async function kickPlayer(targetId) {
  if (!confirm(`确定要踢出玩家 ${targetId} 吗？`)) return
  try {
    await axios.post(`/api/room/${roomId}/kick/${targetId}`)
  } catch (e) {
    err.value = e.response?.data || '踢人失败'
  }
}

function copy() {
  navigator.clipboard.writeText(roomId)
}
</script>

<style scoped>
.room-lobby { max-width: 520px; margin: 40px auto; display: flex; flex-direction: column; gap: 16px; }
.room-id { display: flex; align-items: center; gap: 10px; font-size: 1.1rem; }
.players { display: flex; flex-direction: column; gap: 6px; }
.player-item { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 6px 10px; background: #1a1a2e; border-radius: 4px; }
.player-name { flex: 1; }
.char-select { padding: 2px 4px; font-size: 0.85rem; }
.char-display { font-size: 0.9rem; color: #4a9eff; min-width: 80px; text-align: center; }
.ready { color: #7fff7f; }
.not-ready { color: #ff7f7f; font-size: 0.8rem; }
.kick-btn { padding: 2px 8px; font-size: 0.75rem; background: #e94560; color: #fff; border: none; border-radius: 3px; cursor: pointer; }
.shop { background: #16213e; border-radius: 8px; padding: 12px; }
.shop h3 { margin: 0 0 8px; display: flex; justify-content: space-between; align-items: center; }
.coins { color: #ffd700; font-size: 1rem; }
.shop-items { display: flex; flex-wrap: wrap; gap: 8px; }
.shop-item { background: #1a1a2e; border-radius: 6px; padding: 8px; flex: 1; min-width: 130px; display: flex; flex-direction: column; gap: 2px; }
.si-name { font-weight: bold; }
.si-desc { font-size: 0.75rem; color: #aaa; }
.si-price { font-size: 0.85rem; color: #ffd700; }
.buy-btn { margin-top: 4px; padding: 4px 8px; font-size: 0.8rem; background: #4a9eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.buy-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.inventory { margin-top: 8px; }
.inventory h4 { margin: 4px 0; font-size: 0.9rem; }
.item-tag { display: inline-block; background: #0f3460; padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; margin: 2px; }
.remove-btn { margin-left: 4px; color: #ff6b6b; cursor: pointer; font-weight: bold; border: none; background: none; font-size: 0.9rem; }
.actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.ready-btn { padding: 8px 20px; background: #4a9eff; color: white; border: none; border-radius: 6px; cursor: pointer; }
.ready-btn:disabled { opacity: 0.5; }
.cancel-btn { padding: 8px 20px; background: #666; color: white; border: none; border-radius: 6px; cursor: pointer; }
.start-btn { padding: 10px 20px; background: #e94560; color: white; border: none; border-radius: 6px; cursor: pointer; }
.start-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.err { color: red; }
</style>
