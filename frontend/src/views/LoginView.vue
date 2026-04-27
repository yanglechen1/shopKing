<template>
  <div class="auth-wrap">
    <h2>{{ isLogin ? '登录' : '注册' }}</h2>
    <input v-model="form.username" placeholder="用户名" />
    <input v-model="form.password" type="password" placeholder="密码" />
    <input v-if="!isLogin" v-model="form.nickname" placeholder="昵称" />
    <button @click="submit">{{ isLogin ? '登录' : '注册' }}</button>
    <p class="err" v-if="err">{{ err }}</p>
    <a @click="isLogin = !isLogin">{{ isLogin ? '没有账号？注册' : '已有账号？登录' }}</a>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const isLogin = ref(true)
const err = ref('')
const form = reactive({ username: '', password: '', nickname: '' })

async function submit() {
  err.value = ''
  try {
    if (isLogin.value) {
      await auth.login(form.username, form.password)
    } else {
      await auth.register(form.username, form.password, form.nickname)
      isLogin.value = true
      return
    }
    router.push('/lobby')
  } catch (e) {
    err.value = e.response?.data?.message || '操作失败'
  }
}
</script>

<style scoped>
.auth-wrap { max-width: 320px; margin: 100px auto; display: flex; flex-direction: column; gap: 10px; }
.err { color: red; }
a { cursor: pointer; color: #4a9eff; }
</style>
