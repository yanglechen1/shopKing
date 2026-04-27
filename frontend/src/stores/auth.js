import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const playerId = ref(localStorage.getItem('playerId') || '')

  async function login(username, password) {
    const res = await axios.post('/api/auth/login', { username, password })
    token.value = res.data.token
    playerId.value = String(res.data.userId)
    localStorage.setItem('token', token.value)
    localStorage.setItem('playerId', playerId.value)
    axios.defaults.headers.common['Authorization'] = 'Bearer ' + token.value
  }

  async function register(username, password, nickname) {
    await axios.post('/api/auth/register', { username, password, nickname })
  }

  function logout() {
    token.value = ''
    playerId.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('playerId')
    delete axios.defaults.headers.common['Authorization']
  }

  // 初始化时设置 axios header
  if (token.value) axios.defaults.headers.common['Authorization'] = 'Bearer ' + token.value

  return { token, playerId, login, register, logout }
})
