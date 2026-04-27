<template>
  <div class="modal-mask" @click.self="$emit('close')">
    <div class="modal">
      <h3>本局配置</h3>
      <label>仓库主题
        <select v-model="local.warehouseTheme" :disabled="!editable">
          <option v-for="t in themeOptions" :key="t.key" :value="t.key">{{ t.name }}</option>
          <option value="RANDOM">随机</option>
        </select>
      </label>
      <label>仓库地区
        <select v-model="local.warehouseRegion" :disabled="!editable">
          <option v-for="r in regionOptions" :key="r.key" :value="r.key">{{ r.name }}</option>
          <option value="RANDOM">随机</option>
        </select>
      </label>
      <label>出价倒计时(秒) <input v-model.number="local.bidTimeSecs"    type="number" :disabled="!editable" /></label>
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
import { ref, watch, computed } from 'vue'

const props = defineProps({ config: Object, editable: Boolean, roomId: String })
const emit = defineEmits(['close', 'saved'])
import axios from 'axios'

const local = ref({ ...props.config })
const saved = ref(false)

// 同步外部 config 变化
watch(() => props.config, v => { local.value = { ...v } })

const themeOptions = computed(() => {
  try {
    if (typeof props.config.warehouseThemes === 'string')
      return JSON.parse(props.config.warehouseThemes)
    if (Array.isArray(props.config.warehouseThemes))
      return props.config.warehouseThemes
  } catch (e) { /* ignore */ }
  return []
})

const regionOptions = computed(() => {
  try {
    if (typeof props.config.warehouseRegions === 'string')
      return JSON.parse(props.config.warehouseRegions)
    if (Array.isArray(props.config.warehouseRegions))
      return props.config.warehouseRegions
  } catch (e) { /* ignore */ }
  return []
})

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
