<script setup>
import { computed } from 'vue'
import { fmt } from '@/components/charts/options'

/**
 * 排名列表（学院志愿时长）。对应原型的 .ink-rank-row：
 * 序号 + 名称 + 墨线比例条 + 数值。
 * 比例以首行为基准 —— 约定传入数据已按降序排列。
 */
const props = defineProps({
  /** [{ college, hours }]，按 hours 降序 */
  rows: { type: Array, default: () => [] },
  unit: { type: String, default: ' 小时' },
  limit: { type: Number, default: 8 },
})

const visible = computed(() => props.rows.slice(0, props.limit))
const max = computed(() => (visible.value.length ? visible.value[0].hours : 1))

const widthOf = (row) => `${Math.round((row.hours / (max.value || 1)) * 100)}%`
</script>

<template>
  <ol class="ink-rank anim-stagger">
    <li v-for="(row, i) in visible" :key="row.college" class="ink-rank-row">
      <span class="ink-rank-no num">{{ i + 1 }}</span>
      <span class="ink-rank-name">{{ row.college }}</span>
      <span class="ink-rank-bar" aria-hidden="true">
        <i :style="{ width: widthOf(row) }"></i>
      </span>
      <span class="ink-rank-val">
        <b class="ink-rank-num num">{{ fmt(row.hours) }}</b>
        <span class="ink-rank-unit">{{ unit }}</span>
      </span>
    </li>
  </ol>
</template>