<template>
  <div class="reveal">
    <h3>开箱揭晓</h3>
    <div v-for="(item, i) in revealedItems" :key="i" :class="['item', item.quality?.toLowerCase()]">
      <span>{{ item.name }}</span>
      <span class="quality">{{ item.quality }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ messages: Array })

const revealedItems = computed(() =>
  props.messages
    .filter(m => m.type === 'ITEM_REVEALED')
    .map(m => m.item)
)
</script>

<style scoped>
.reveal { display: flex; flex-direction: column; gap: 8px; }
.item { display: flex; justify-content: space-between; padding: 8px 12px; border-radius: 4px; background: #1a1a2e; }
.common   { border-left: 4px solid #6699ff; }
.advanced { border-left: 4px solid #44aaff; }
.rare     { border-left: 4px solid #aa44ff; }
.epic     { border-left: 4px solid #cc44ff; }
.legend   { border-left: 4px solid #ff8800; }
.myth     { border-left: 4px solid #ff4400; }
.quality  { font-size: 0.8rem; opacity: 0.7; }
</style>
