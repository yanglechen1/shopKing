<template>
  <div class="lobby">
    <h2>大厅</h2>
    <button @click="createRoom">创建房间</button>
    <button @click="router.push('/config')">游戏配置</button>
    <button @click="auth.logout(); router.push('/login')">退出</button>

    <div class="join">
      <input v-model="joinId" placeholder="输入房间ID" />
      <button @click="joinRoom">加入</button>
    </div>
    <p class="err" v-if="err">{{ err }}</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import axios from 'axios'

const router = useRouter()
const auth = useAuthStore()
const joinId = ref('')
const err = ref('')

async function createRoom() {
  try {
    const res = await axios.post('/api/room/create')
    router.push('/room/' + res.data.roomId + '/lobby')
  } catch (e) {
    err.value = e.response?.data?.message || e.response?.data || '创建失败'
  }
}

async function joinRoom() {
  try {
    await axios.post(`/api/room/${joinId.value}/join`)
    router.push('/room/' + joinId.value + '/lobby')
  } catch (e) {
    err.value = e.response?.data || '加入失败'
  }
}
</script>

<style scoped>
.lobby { max-width: 400px; margin: 60px auto; display: flex; flex-direction: column; gap: 12px; }
.join { display: flex; gap: 8px; }
.err { color: red; }
</style>
