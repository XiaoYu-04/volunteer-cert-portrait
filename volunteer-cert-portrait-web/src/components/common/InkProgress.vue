<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, required: true },
  max: { type: Number, default: 100 },
  /** 是否在右侧显示 '46 / 60' 这类计数 */
  showText: { type: Boolean, default: false },
  /** 自定义右侧文字，给了就覆盖 showText 的自动计算 */
  text: { type: String, default: '' },
  /** 无障碍名称：只说进度的含义（如「报名进度」），不重复数值 */
  label: { type: String, default: '' },
})

const percent = computed(() => {
  if (!props.max) return 0
  return Math.min(100, Math.max(0, (props.value / props.max) * 100))
})

const displayText = computed(() => props.text || `${props.value} / ${props.max}`)
</script>

<template>
  <!-- 语义做进组件：内部两根 span 是装饰，调用方只需给 label，不再各自抄一遍 aria-* -->
  <div
    class="ink-progress"
    role="progressbar"
    :aria-label="label || undefined"
    :aria-valuenow="value"
    :aria-valuemin="0"
    :aria-valuemax="max > 0 ? max : 100"
  >
    <span class="ink-progress-track">
      <span class="ink-progress-bar" :style="{ width: `${percent}%` }"></span>
    </span>
    <span v-if="showText || text" class="ink-progress-text num">{{ displayText }}</span>
  </div>
</template>