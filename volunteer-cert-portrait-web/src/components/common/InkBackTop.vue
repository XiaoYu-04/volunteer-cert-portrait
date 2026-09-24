<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 返回顶部。固定在视口右侧中间（竖排文字，沿用原型的竖排标题写法），
 * 滚动超过一屏才出现 —— 首屏没有可返回的「上」，常驻反而挡内容。
 *
 * 挂在 App.vue 上，学生端与控制台共用一份；用的是 window 滚动，
 * 两种布局的滚动容器都是文档本身（.console-side 是 sticky，不截获滚动）。
 */
const SHOW_AFTER = 400

const show = ref(false)
const onScroll = () => {
  show.value = window.scrollY > SHOW_AFTER
}

function toTop() {
  // 尊重「减少动态效果」偏好：这类用户对平滑滚动会不适
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <button
    class="ink-to-top"
    :class="{ 'is-show': show }"
    type="button"
    aria-label="返回顶部"
    title="返回顶部"
    @click="toTop"
  >
    <span class="ink-to-top-arrow" aria-hidden="true">↑</span>
    <span class="ink-to-top-text">返回顶部</span>
  </button>
</template>