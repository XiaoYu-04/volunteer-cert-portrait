<script setup>
import { useToast } from '@/composables/useToast'

const { items } = useToast()
</script>

<template>
  <Teleport to="body">
    <!-- TransitionGroup：离场淡出 + move 补位，两类过渡见 ink.css 的 .ink-toast-* -->
    <TransitionGroup
      name="ink-toast"
      tag="div"
      class="ink-toast-stack"
      aria-live="polite"
      aria-atomic="false"
    >
      <div v-for="item in items" :key="item.id" class="ink-toast" :class="`tone-${item.tone}`">
        {{ item.message }}
      </div>
    </TransitionGroup>
  </Teleport>
</template>