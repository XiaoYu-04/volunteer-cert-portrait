<script setup>
import { ref, shallowRef, watch, onMounted, onBeforeUnmount } from 'vue'
import echarts from './echarts'

const props = defineProps({
  /** ECharts option，由 options.js 的工厂函数生成 */
  option: { type: Object, default: null },
  /** 容器高度，直接落到内联样式上；ECharts 需要显式高度才能渲染 */
  height: { type: String, default: '280px' },
  /** 无障碍描述，会写到 role="img" 容器的 aria-label */
  label: { type: String, default: '' },
  loading: { type: Boolean, default: false },
})

const el = ref(null)
const chart = shallowRef(null)
let observer = null

function render() {
  if (!chart.value || !props.option) return
  // notMerge=true：换图表类型时避免残留上一次的 series
  chart.value.setOption(props.option, true)
}

onMounted(() => {
  if (!el.value) return
  chart.value = echarts.init(el.value, null, { renderer: 'canvas' })
  render()

  // 容器宽度随布局变化时（侧边栏折叠、断点切换）同步图表尺寸
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => {
      if (chart.value) chart.value.resize()
    })
    observer.observe(el.value)
  }
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (chart.value) {
    chart.value.dispose()
    chart.value = null
  }
})

watch(() => props.option, render, { deep: true })
</script>

<template>
  <div class="ink-chart">
    <div
      ref="el"
      class="ink-chart-canvas"
      :style="{ height }"
      role="img"
      :aria-label="label || undefined"
    ></div>
    <div v-if="loading" class="ink-chart-loading" aria-hidden="true">
      <span class="ink-skeleton ink-skeleton-row"></span>
      <span class="ink-skeleton ink-skeleton-row"></span>
      <span class="ink-skeleton ink-skeleton-row"></span>
    </div>
  </div>
</template>

<style scoped>
.ink-chart {
  position: relative;
  width: 100%;
}

.ink-chart-canvas {
  width: 100%;
}

.ink-chart-loading {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 24px;
  background: var(--c-panel);
}

.ink-chart-loading .ink-skeleton-row {
  height: 12px;
  margin-bottom: 16px;
}
</style>