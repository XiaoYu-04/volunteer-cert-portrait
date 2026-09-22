<script setup>
import { computed } from 'vue'
import { confirmState, settleConfirm } from '@/composables/useConfirm'
import InkDialog from './InkDialog.vue'

const open = computed({
  get: () => !!confirmState.value,
  set: (v) => {
    if (!v) settleConfirm(false)
  },
})
</script>

<template>
  <InkDialog v-model="open" :title="confirmState?.title" size="narrow">
    <p class="ink-dialog-text">{{ confirmState?.message }}</p>

    <template #footer>
      <button class="btn btn-ghost" type="button" @click="settleConfirm(false)">
        {{ confirmState?.cancelText }}
      </button>
      <button
        class="btn"
        :class="confirmState?.tone === 'danger' ? 'btn-danger' : 'btn-primary'"
        type="button"
        @click="settleConfirm(true)"
      >
        {{ confirmState?.confirmText }}
      </button>
    </template>
  </InkDialog>
</template>