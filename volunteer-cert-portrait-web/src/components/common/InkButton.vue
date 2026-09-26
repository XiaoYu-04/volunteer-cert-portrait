<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  /** default | primary | ghost | danger */
  variant: { type: String, default: 'default' },
  /** md | sm */
  size: { type: String, default: 'md' },
  /** 传了 to 就渲染成 RouterLink，用于导航型按钮 */
  to: { type: [String, Object], default: null },
  disabled: { type: Boolean, default: false },
  /** 原生 button 的 type，默认 button 避免在表单里意外提交 */
  nativeType: { type: String, default: 'button' },
})

const tag = computed(() => (props.to ? RouterLink : 'button'))

/**
 * 导航型按钮（RouterLink）没有原生 disabled：只加 aria-disabled 的话，
 * 键盘 Tab 与回车仍然能聚焦、能跳转 —— 视觉上"禁用"、行为上没禁用。
 * 这里在点击入口拦住，并把它从 Tab 序列里摘掉。
 */
function onClick(event) {
  if (props.to && props.disabled) event.preventDefault()
}

const classes = computed(() => [
  'btn',
  props.variant !== 'default' && `btn-${props.variant}`,
  props.size === 'sm' && 'btn-sm',
])
</script>

<template>
  <component
    :is="tag"
    :class="classes"
    :to="to || undefined"
    :type="to ? undefined : nativeType"
    :disabled="to ? undefined : disabled"
    :tabindex="to && disabled ? -1 : undefined"
    :aria-disabled="to && disabled ? 'true' : undefined"
    @click="onClick"
  >
    <slot />
  </component>
</template>