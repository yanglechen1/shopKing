import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from './auth'

export const useGameStore = defineStore('game', () => {
  // 游戏状态
  const state = ref(null)
  const round = ref(0)
  const deadlineTs = ref(0)
  const players = ref([])
  const bidCount = computed(() => Object.keys(playerBids.value).length)
  const feedback = ref(null)      // 本轮出价反馈
  const skillResult = ref(null)   // 技能执行结果
  const messages = ref([])        // 所有广播消息（含开箱帧）
  const settled = ref(null)       // 结算结果
  const playerItems = ref([])     // 我的背包道具列表
  const playerCoins = ref(0)      // 我的当前金币
  const playerBids = ref({})      // 各玩家出价状态 {playerId: true}
  const allBids = ref(null)       // 本轮所有出价信息 {bids:{}} 或 {ranking:[]}
  const roundHistory = ref([])     // 多轮历史 [{round, bids/ranking, items}, ...]
  const itemResults = ref([])     // 道具使用结果记录 [{itemType, message, round}, ...]

  let client = null

  /**
   * 连接 WebSocket，token 通过 query param 传递（SockJS 握手时 Header 不可靠）
   * 连接成功后订阅房间广播、个人队列，并触发重连快照（仅游戏进行中有效）
   */
  function connect(roomId) {
    // 重置状态，避免上一局残留影响新局
    state.value = null
    round.value = 0
    deadlineTs.value = 0
    feedback.value = null
    skillResult.value = null
    messages.value = []
    settled.value = null
    playerItems.value = []
    playerCoins.value = 0
    playerBids.value = {}
    allBids.value = null
    roundHistory.value = []
    itemResults.value = []

    const auth = useAuthStore()
    client = new Client({
      webSocketFactory: () => new SockJS(`/ws?token=${auth.token}`),
      onConnect: () => {
        // 房间广播（状态变更、轮次、开箱帧等）
        client.subscribe(`/topic/room/${roomId}`, ({ body }) => {
          const msg = JSON.parse(body)
          console.log('[WS] 收到广播:', msg)
          handleBroadcast(msg)
        })
        // 个人出价反馈
        client.subscribe('/user/queue/feedback', ({ body }) => {
          const msg = JSON.parse(body)
          console.log('[WS] 收到反馈:', msg)
          feedback.value = msg
        })
        // 断线重连快照（WAITING 阶段后端不响应）
        client.subscribe('/user/queue/reconnect', ({ body }) => {
          const snap = JSON.parse(body)
          console.log('[WS] 收到重连快照:', snap)
          if (snap.action === 'TO_LOBBY') { window.location.href = '/lobby'; return }
          state.value = snap.state
          round.value = snap.round
          deadlineTs.value = snap.deadlineTs
          // 恢复本轮已出价的玩家列表（断线重连时左侧面板需要）
          if (snap.bidderIds && Array.isArray(snap.bidderIds)) {
            const restored = {}
            snap.bidderIds.forEach(pid => { restored[pid] = true })
            playerBids.value = restored
          }
          // 恢复背包信息（由 reconnect 后端附带）
          if (snap.items) playerItems.value = snap.items
          if (snap.coins != null) playerCoins.value = snap.coins
        })
        // 错误消息
        client.subscribe('/user/queue/error', ({ body }) => {
          const msg = JSON.parse(body)
          console.log('[WS] 收到错误:', msg)
          messages.value.push({ type: 'error', text: msg.error })
        })
        // 道具信息（透视卡等）
        client.subscribe('/user/queue/item-info', ({ body }) => {
          const msg = JSON.parse(body)
          console.log('[WS] 收到道具信息:', msg)
          if (msg.type === 'PEEK_TOTAL') {
            messages.value.push({ type: 'item-info', text: `仓库总价值: ${msg.totalValue} 金币` })
          }
        })
        // 道具使用结果（独立使用道具后的返回）
        client.subscribe('/user/queue/item-result', ({ body }) => {
          const msg = JSON.parse(body)
          console.log('[WS] 收到道具使用结果:', msg)
          itemResults.value = [...itemResults.value, { itemType: msg.itemType, message: msg.message, round: round.value }]
        })
        // 触发重连快照（传入 roomId，防止旧局污染新局）
        // 背包信息由 reconnect 快照中的 coins/items 字段恢复
        client.publish({ destination: '/app/reconnect', body: JSON.stringify({ roomId }) })
      }
    })
    client.activate()
  }

  function disconnect() {
    client?.deactivate()
  }

  /** 提交出价 */
  function submitBid(roomId, amount) {
    client.publish({
      destination: '/app/room/bid',
      body: JSON.stringify({ roomId, amount, clientTs: Date.now() })
    })
  }

  /** 使用道具（独立于出价，先使用再出价） */
  function useItem(roomId, itemType) {
    client.publish({
      destination: '/app/room/useItem',
      body: JSON.stringify({ roomId, itemType })
    })
  }

  /** 处理服务端广播，更新本地状态 */
  function handleBroadcast(msg) {
    messages.value.push(msg)
    if (msg.type === 'ROUND_START') {
      state.value = 'BIDDING'
      round.value = msg.payload.round
      deadlineTs.value = msg.payload.deadlineTs
      playerBids.value = {}
      allBids.value = null
    } else if (msg.type === 'SKILL_PHASE_START') {
      state.value = 'SKILL_PHASE'
      round.value = msg.payload.round
      playerBids.value = {}
      allBids.value = null
    } else if (msg.type === 'BID_PLACED') {
      playerBids.value = { ...playerBids.value, [msg.playerId]: true }
    } else if (msg.type === 'ALL_BIDS') {
      allBids.value = msg.payload
      // 累积多轮出价历史（左侧面板展示用）
      roundHistory.value = [...roundHistory.value, { round: round.value, ...msg.payload }]
    } else if (msg.type === 'TIE_BREAK') {
      state.value = 'TIE_BREAK'
    } else if (msg.type === 'REVEALING_START') {
      state.value = 'REVEALING'
    } else if (msg.type === 'GAME_FINISHED') {
      // 保留当前状态，GAME_SETTLED 中已有结算数据
    } else if (msg.type === 'GAME_FORCE_END') {
      state.value = 'FINISHED'
      settled.value = { winnerId: '无', finalBid: 0, warehouseValue: 0, profit: 0 }
    } else if (msg.type === 'GAME_SETTLED') {
      settled.value = msg
    } else if (msg.type === 'GAME_RESTART') {
      // 回到准备大厅
      window.location.href = '/room/' + (window.location.pathname.match(/\/room\/([^/]+)/)?.[1] || '') + '/lobby'
    }
  }

  return {
    state, round, deadlineTs, players, bidCount,
    feedback, skillResult, messages, settled,
    playerItems, playerCoins, playerBids, allBids, roundHistory, itemResults,
    connect, disconnect, submitBid, useItem
  }
})
