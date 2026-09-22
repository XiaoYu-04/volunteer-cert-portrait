<script setup>
import { computed } from 'vue'
import { formatNumber } from '@/utils/format'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], required: true },
  unit: { type: String, default: '' },
  /** 环比变化，百分数数值，如 12.0 表示 +12.0% */
  delta: { type: Number, default: null },
  /** up | down，缺省时按 delta 正负推断 */
  trend: { type: String, default: '' },
  /** delta 的说明文字 */
  deltaLabel: { type: String, default: '较上月' },
})

const direction = computed(() => {
  if (props.trend) return props.trend
  if (props.delta === null) return ''
  return props.delta < 0 ? 'down' : 'up'
})

const displayValue = computed(() =>
  typeof props.value === 'number' ? formatNumber(props.value) : props.value,
)
</script>

<template>
  <div class="ink-stat-card">
    <span class="ink-stat-label">{{ label }}</span>
    <span class="ink-stat-value">
      <span class="ink-stat-num num">{{ displayValue }}</span>
      <span v-if="unit" class="ink-stat-unit">{{ unit }}</span>
    </span>
    <span v-if="delta !== null" class="ink-stat-delta" :class="`is-${direction}`">
      <span class="ink-stat-arrow" aria-hidden="true">{{ direction === 'down' ? '↓' : '↑' }}</span>
      <span class="ink-stat-delta-num num">{{ Math.abs(delta).toFixed(1) }}%</span>
      <span class="ink-stat-delta-cap">{{ deltaLabel }}</span>
    </span>
  </div>
</template>