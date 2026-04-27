<template>
  <div class="config">
    <h2>游戏配置</h2>
    <label>玩家人数 <input v-model.number="cfg.playerCount" type="number" min="2" max="8" /></label>
    <label>初始金币 <input v-model.number="cfg.initialCoins" type="number" /></label>
    <label>出价倒计时(秒) <input v-model.number="cfg.bidTimeSecs" type="number" /></label>
    <label>Grace Period(ms) <input v-model.number="cfg.gracePeriodMs" type="number" /></label>
    <label>速胜轮数 <input v-model.number="cfg.speedWinRounds" type="number" /></label>
    <label>暗标模式 <input v-model="cfg.blindBidding" type="checkbox" /></label>
    <label>模糊反馈 <input v-model="cfg.fuzzyFeedback" type="checkbox" /></label>
    <button @click="save">保存</button>
    <button @click="router.back()">返回</button>
    <p class="ok" v-if="saved">已保存</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const saved = ref(false)
const cfg = ref({})

onMounted(async () => {
  const res = await axios.get('/api/config')
  cfg.value = res.data
})

async function save() {
  await axios.put('/api/config', cfg.value)
  saved.value = true
  setTimeout(() => saved.value = false, 2000)
}
</script>

<style scoped>
.config { max-width: 360px; margin: 60px auto; display: flex; flex-direction: column; gap: 10px; }
label { display: flex; justify-content: space-between; align-items: center; }
.ok { color: green; }
</style>
