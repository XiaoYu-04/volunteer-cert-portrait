<script setup>
import { watch, onBeforeUnmount, nextTick, ref } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  /** normal | wide | narrow */
  size: { type: String, default: 'normal' },
  closeOnMask: { type: Boolean, default: true },
  showClose: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'close'])

const panel = ref(null)

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function onMaskDown(e) {
  if (props.closeOnMask && e.target === e.currentTarget) close()
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

// 打开时锁滚动 + 监听 Esc + 把焦点移进弹窗
watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
      await nextTick()
      const focusable = panel.value?.querySelector(
        'input, select, textarea, button, [href], [tabindex]:not([tabindex="-1"])',
      )
      focusable?.focus()
    } else {
      document.body.style.overflow = ''
      document.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <!-- Transition 只接管离场（.ink-dialog-leave-*）：入场仍是 ink.css 的
         ink-mask-in / ink-dialog-in 动画，不写 enter 类，避免动画与过渡双跑 -->
    <Transition name="ink-dialog">
      <div
        v-if="modelValue"
        class="ink-dialog-mask"
        role="dialog"
        aria-modal="true"
        :aria-label="title || undefined"
        @mousedown="onMaskDown"
      >
        <div
          ref="panel"
          class="ink-dialog"
          :class="{ 'is-wide': size === 'wide', 'is-narrow': size === 'narrow' }"
        >
          <div v-if="title || showClose" class="ink-dialog-head">
            <h3 class="ink-dialog-title">{{ title }}</h3>
            <button
              v-if="showClose"
              class="ink-dialog-close"
              type="button"
              aria-label="关闭"
              @click="close"
            >
              ×
            </button>
          </div>

          <div class="ink-dialog-body">
            <slot />
          </div>

          <div v-if="$slots.footer" class="ink-dialog-foot">
            <slot name="footer" :close="close" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>