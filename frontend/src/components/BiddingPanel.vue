<template>
  <div class="bidding">
    <div class="countdown">{{ remaining }}s</div>

    <!-- 道具选择 -->
    <div class="item-row" v-if="game.playerItems && game.playerItems.length > 0 && !submitted">
      <select v-model="selectedItem" class="item-select">
        <option value="">不使用道具</option>
        <option v-for="(it, i) in uniqueItems" :key="i" :value="it">
          {{ getItemName(it) }}
        </option>
      </select>
      <span class="item-hint" v-if="selectedItem">出价时将使用道具</span>
    </div>

    <div class="bid-row">
      <input v-model.number="amount" type="number" :disabled="submitted" placeholder="输入出价" class="bid-input" />
      <button @click="submit" :disabled="submitted || remaining <= 0" class="bid-btn">
        {{ submitted ? '已出价' : selectedItem ? (getItemName(selectedItem) + '+出价') : '提交出价' }}
      </button>
    </div>

    <p v-if="submitted" class="bid-done">已出价</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useGameStore } from '../stores/game'

const props = defineProps({ deadlineTs: Number, roomId: String })
const game = useGameStore()
const amount = ref(0)
const submitted = ref(false)
const remaining = ref(0)
const selectedItem = ref('')

const itemNames = {
  DOUBLE_BID: '翻倍卡', BID_INSURANCE: '保险卡',
  PEEK_TOTAL: '透视卡', EXTRA_3000: '加价券',
  HALF_DISCOUNT: '截胡卡'
}

function getItemName(type) { return itemNames[type] || type }

// 去重（同一个道具可能多个）
const uniqueItems = computed(() => [...new Set(game.playerItems || [])])

let timer = null

onMounted(() => {
  timer = setInterval(() => {
    remaining.value = Math.max(0, Math.ceil((props.deadlineTs - Date.now()) / 1000))
  }, 200)
})

onUnmounted(() => clearInterval(timer))

function submit() {
  if (submitted.value || amount.value <= 0) return
  const itemType = selectedItem.value || null
  game.submitBid(props.roomId, amount.value, itemType)
  submitted.value = true
}
</script>

<style scoped>
.bidding { display: flex; flex-direction: column; gap: 8px; }
.countdown { font-size: 2rem; font-weight: bold; color: #e94560; }
.item-row { display: flex; align-items: center; gap: 8px; }
.item-select { padding: 4px 6px; font-size: 0.9rem; flex: 1; }
.item-hint { font-size: 0.75rem; color: #4a9eff; }
.bid-row { display: flex; gap: 8px; }
.bid-input { flex: 1; padding: 6px; font-size: 1rem; }
.bid-btn { padding: 8px 16px; background: #4a9eff; color: white; border: none; border-radius: 6px; cursor: pointer; white-space: nowrap; }
.bid-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.bid-done { color: #7fff7f; font-size: 0.9rem; }
</style>
