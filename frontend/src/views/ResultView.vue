<template>
  <div class="result">
    <h2>结算结果</h2>
    <p>获胜者: {{ data.winnerId }}</p>
    <p>中标价: {{ data.finalBid }}</p>
    <p>仓库价值: {{ data.warehouseValue }}</p>
    <p>利润: {{ data.profit }}</p>
    <p>总轮次: {{ data.totalRounds }}</p>
    <button @click="router.push('/lobby')">返回大厅</button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const data = ref({})

onMounted(async () => {
  const res = await axios.get(`/api/record/${route.params.id}`)
  data.value = res.data
})
</script>

<style scoped>
.result { max-width: 400px; margin: 60px auto; display: flex; flex-direction: column; gap: 12px; }
</style>
