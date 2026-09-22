<script setup>
import { useToast } from '@/composables/useToast'

const { items } = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="ink-toast-stack" aria-live="polite" aria-atomic="false">
      <div v-for="item in items" :key="item.id" class="ink-toast" :class="`tone-${item.tone}`">
        {{ item.message }}
      </div>
    </div>
  </Teleport>
</template>