<script setup>
defineProps({
  text: { type: String, default: '暂无数据' },
  hint: { type: String, default: '' },
  /** 印章式的装饰字，默认取「空」 */
  mark: { type: String, default: '空' },
})
</script>

<template>
  <div class="ink-empty">
    <p class="ink-empty-mark" aria-hidden="true">{{ mark }}</p>
    <p class="ink-empty-text">{{ text }}</p>
    <p v-if="hint" class="ink-empty-hint">{{ hint }}</p>
    <slot />
  </div>
</template>