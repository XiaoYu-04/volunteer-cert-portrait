<script setup>
import { computed } from 'vue'
import { useDictStore } from '@/stores/dict'

/**
 * 状态标签。传字典类型 + 英文码，标签与色调都从 dict store 取，
 * 页面里不出现硬编码的中文状态字符串。
 */
const props = defineProps({
  /** 字典类型，如 'signup_status' */
  type: { type: String, required: true },
  /** 状态英文码，如 'PENDING' */
  value: { type: String, default: '' },
})

const dict = useDictStore()

const label = computed(() => dict.label(props.type, props.value))
const tone = computed(() => dict.tone(props.type, props.value))
</script>

<template>
  <span class="ink-status" :class="`tone-${tone}`">{{ label }}</span>
</template>