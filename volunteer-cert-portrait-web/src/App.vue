<script setup>
import InkToast from '@/components/common/InkToast.vue'
import InkConfirm from '@/components/common/InkConfirm.vue'
</script>

<template>
  <RouterView />

  <!-- 全局反馈：轻提示与确认框，挂在根组件，任意页面通过 composable 唤起 -->
  <InkToast />
  <InkConfirm />
</template>