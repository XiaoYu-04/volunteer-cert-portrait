<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  /** [{ value, label }] */
  tabs: { type: Array, required: true },
})

defineEmits(['update:modelValue'])
</script>

<template>
  <div class="ink-tabs" role="tablist">
    <button
      v-for="tab in tabs"
      :key="tab.value"
      class="ink-tab"
      :class="{ 'is-current': tab.value === modelValue }"
      type="button"
      role="tab"
      :aria-selected="tab.value === modelValue"
      @click="$emit('update:modelValue', tab.value)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>