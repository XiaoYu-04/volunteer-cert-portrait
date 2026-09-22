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
  block: { type: Boolean, default: false },
  /** 原生 button 的 type，默认 button 避免在表单里意外提交 */
  nativeType: { type: String, default: 'button' },
})

const tag = computed(() => (props.to ? RouterLink : 'button'))

const classes = computed(() => [
  'btn',
  props.variant !== 'default' && `btn-${props.variant}`,
  props.size === 'sm' && 'btn-sm',
  props.block && 'btn-block',
])
</script>

<template>
  <component
    :is="tag"
    :class="classes"
    :to="to || undefined"
    :type="to ? undefined : nativeType"
    :disabled="to ? undefined : disabled"
    :aria-disabled="to && disabled ? 'true' : undefined"
  >
    <slot />
  </component>
</template>