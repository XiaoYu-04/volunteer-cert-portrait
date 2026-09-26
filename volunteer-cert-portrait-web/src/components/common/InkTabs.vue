<script setup>
import { nextTick, ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  /** [{ value, label }] */
  tabs: { type: Array, required: true },
  /**
   * 传了就把 tab 与面板按 WAI-ARIA 的 id 约定连起来：
   *   tab   → `${panelId}-tab-${value}`
   *   面板  → `${panelId}-panel-${value}`
   * 面板由调用方渲染（加 role="tabpanel" 与对应 id），组件不持有它。
   */
  panelId: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const root = ref(null)

/** 选中项下标；没有匹配项时退回第一个，保证键盘 Tab 永远进得来 */
const activeIndex = () => {
  const i = props.tabs.findIndex((tab) => tab.value === props.modelValue)
  return i < 0 ? 0 : i
}

/**
 * 方向键 / Home / End：按 WAI-ARIA 的「自动激活」模式 —— 焦点与选中一起移动。
 * 页面上的 tab 都是筛选开关，独立的一次方向键就应看到结果变化；
 * 手动激活（先移焦点、再按 Enter 才切换）会让筛选多一步，反而不符合预期。
 */
function onKeydown(event) {
  const count = props.tabs.length
  if (!count) return
  const current = activeIndex()
  let next = null
  if (event.key === 'ArrowRight') next = (current + 1) % count
  else if (event.key === 'ArrowLeft') next = (current - 1 + count) % count
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = count - 1
  if (next === null) return
  event.preventDefault()
  emit('update:modelValue', props.tabs[next].value)
  nextTick(() => {
    root.value?.querySelectorAll('[role="tab"]')[next]?.focus()
  })
}
</script>

<template>
  <div ref="root" class="ink-tabs" role="tablist" @keydown="onKeydown">
    <button
      v-for="(tab, i) in tabs"
      :key="tab.value"
      class="ink-tab"
      :class="{ 'is-current': tab.value === modelValue }"
      type="button"
      role="tab"
      :id="panelId ? `${panelId}-tab-${tab.value}` : undefined"
      :aria-controls="panelId ? `${panelId}-panel-${tab.value}` : undefined"
      :aria-selected="tab.value === modelValue"
      :tabindex="i === activeIndex() ? 0 : -1"
      @click="$emit('update:modelValue', tab.value)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>
