<template>
  <div :class="['banner', feedback.feedback?.toLowerCase()]">
    <span>第 {{ feedback.round }} 轮反馈：</span>
    <strong>{{ labels[feedback.feedback] || feedback.feedback }}</strong>
    <span v-if="feedback.highestBid">（最高价: {{ feedback.highestBid }}）</span>
  </div>
</template>

<script setup>
defineProps({ feedback: Object })
const labels = {
  LEADING: '领先',
  CLOSE: '靠后',
  BEHIND: '落后',
  FAR_BEHIND: '远落后'
}
</script>

<style scoped>
.banner { padding: 8px 16px; border-radius: 6px; font-size: 1.1rem; }
.leading   { background: #1a6b3a; color: #7fff7f; }
.close     { background: #4a6b1a; color: #dfff7f; }
.behind    { background: #6b4a1a; color: #ffdf7f; }
.far_behind{ background: #6b1a1a; color: #ff7f7f; }
</style>
