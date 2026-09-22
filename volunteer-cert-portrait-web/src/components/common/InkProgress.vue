<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, required: true },
  max: { type: Number, default: 100 },
  /** 是否在右侧显示 '46 / 60' 这类计数 */
  showText: { type: Boolean, default: false },
  /** 自定义右侧文字，给了就覆盖 showText 的自动计算 */
  text: { type: String, default: '' },
})

const percent = computed(() => {
  if (!props.max) return 0
  return Math.min(100, Math.max(0, (props.value / props.max) * 100))
})

const displayText = computed(() => props.text || `${props.value} / ${props.max}`)
</script>

<template>
  <div class="ink-progress">
    <span class="ink-progress-track">
      <span class="ink-progress-bar" :style="{ width: `${percent}%` }"></span>
    </span>
    <span v-if="showText || text" class="ink-progress-text num">{{ displayText }}</span>
  </div>
</template>