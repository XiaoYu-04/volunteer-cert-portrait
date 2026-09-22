<script setup>
/**
 * 表单字段容器：标签 + 控件插槽 + 错误/提示。
 * 控件本身直接用全局的 .ink-input / .ink-select / .ink-textarea 类，
 * 不另做包装组件 —— CSS 已经是这一层的抽象。
 */
defineProps({
  label: { type: String, default: '' },
  required: { type: Boolean, default: false },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
})
</script>

<template>
  <label class="ink-field" :class="{ 'is-error': error }">
    <span v-if="label" class="ink-field-label">
      {{ label }}<span v-if="required" class="req" aria-hidden="true">*</span>
    </span>
    <slot />
    <span v-if="error" class="ink-field-error" role="alert">{{ error }}</span>
    <span v-else-if="hint" class="ink-field-hint">{{ hint }}</span>
  </label>
</template>