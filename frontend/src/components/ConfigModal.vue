<template>
  <div class="modal-mask" @click.self="$emit('close')">
    <div class="modal">
      <h3>本局配置</h3>
      <label>出价倒计时(秒) <input v-model.number="local.bidTimeSecs"    type="number" :disabled="!editable" /></label>
      <label>技能阶段(秒)   <input v-model.number="local.skillPhaseSecs" type="number" :disabled="!editable" /></label>
      <label>拍卖轮次       <input v-model.number="local.totalRounds" type="number" :disabled="!editable" /></label>
      <label>Grace Period(ms) <input v-model.number="local.gracePeriodMs" type="number" :disabled="!editable" /></label>
      <label>暗标模式       <input v-model="local.blindBidding"  type="checkbox" :disabled="!editable" /></label>
      <label>模糊反馈       <input v-model="local.fuzzyFeedback" type="checkbox" :disabled="!editable" /></label>
      <div class="actions">
        <button v-if="editable" @click="save">保存</button>
        <button @click="$emit('close')">关闭</button>
      </div>
      <span v-if="saved" class="ok">已保存</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({ config: Object, editable: Boolean, roomId: String })
const emit = defineEmits(['close', 'saved'])
import axios from 'axios'

const local = ref({ ...props.config })
const saved = ref(false)

// 同步外部 config 变化
watch(() => props.config, v => { local.value = { ...v } })

async function save() {
  await axios.put(`/api/room/${props.roomId}/config`, local.value)
  saved.value = true
  emit('saved', local.value)
  setTimeout(() => saved.value = false, 2000)
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #16213e; padding: 24px; border-radius: 10px; min-width: 320px; display: flex; flex-direction: column; gap: 10px; }
label { display: flex; justify-content: space-between; gap: 12px; }
.actions { display: flex; gap: 8px; margin-top: 4px; }
.ok { color: #7fff7f; }
</style>
