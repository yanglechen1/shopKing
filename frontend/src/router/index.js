import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/',          redirect: '/lobby' },
  { path: '/login',     component: () => import('../views/LoginView.vue') },
  { path: '/lobby',     component: () => import('../views/LobbyView.vue'), meta: { auth: true } },
  { path: '/config',    component: () => import('../views/ConfigView.vue'), meta: { auth: true } },
  { path: '/room/:id/lobby', component: () => import('../views/RoomLobbyView.vue'), meta: { auth: true } },
  { path: '/room/:id',  component: () => import('../views/RoomView.vue'),  meta: { auth: true } },
  { path: '/result/:id',component: () => import('../views/ResultView.vue'), meta: { auth: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.auth && !auth.token) return '/login'
})

export default router
