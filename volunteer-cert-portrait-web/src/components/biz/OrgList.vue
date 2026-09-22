<script setup>
/**
 * 组织活跃度列表。对应原型的 .ink-org-row：
 * 组织名占满剩余宽度，右侧三个等宽体指标 —— 场次 / 签到 / 通过。
 */
defineProps({
  /** [{ org, activities, signRate, passRate }]，比率为 0~1 小数 */
  rows: { type: Array, default: () => [] },
})

const pct = (r) => `${Math.round(r * 100)}%`
</script>

<template>
  <ul class="ink-org">
    <li v-for="row in rows" :key="row.org" class="ink-org-row">
      <span class="ink-org-name">{{ row.org }}</span>
      <span class="ink-org-metrics">
        <span class="ink-org-metric">
          <b class="ink-org-metric-num num">{{ row.activities }}</b>
          <span class="ink-org-metric-cap"> 场</span>
        </span>
        <span class="ink-org-metric">
          <span class="ink-org-metric-cap">签到 </span>
          <b class="ink-org-metric-num num">{{ pct(row.signRate) }}</b>
        </span>
        <span class="ink-org-metric">
          <span class="ink-org-metric-cap">通过 </span>
          <b class="ink-org-metric-num num">{{ pct(row.passRate) }}</b>
        </span>
      </span>
    </li>
  </ul>
</template>