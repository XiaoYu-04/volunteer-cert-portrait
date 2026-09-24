<script setup>
import InkToast from '@/components/common/InkToast.vue'
import InkConfirm from '@/components/common/InkConfirm.vue'
import InkBackTop from '@/components/common/InkBackTop.vue'
</script>

<template>
  <RouterView />

  <!-- 固定在右侧的返回顶部：学生端与控制台共用，滚动超过一屏才出现 -->
  <InkBackTop />

  <!-- 全局反馈：轻提示与确认框，挂在根组件，任意页面通过 composable 唤起 -->
  <InkToast />
  <InkConfirm />
</template>
