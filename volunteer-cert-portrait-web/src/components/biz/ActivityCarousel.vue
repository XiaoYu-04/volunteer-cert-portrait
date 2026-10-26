<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

/**
 * 首页 Hero 右侧的活动影像轮播。
 *
 * 数据由页面传入（只取有封面的活动），组件自身不请求接口。
 * 交互约定：
 *   · 自动轮播 5.2s，悬停 / 键盘聚焦时暂停；
 *   · 手动切换（箭头 / 圆点）后重新计时；
 *   · 系统开启「减少动态效果」时只保留手动切换，不自动播；
 *   · 页面切到后台时停表，回来再恢复。
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  /** 详情页前缀，最终地址为 `${toBase}/${item.id}` */
  toBase: { type: String, default: '/student/activities' },
  interval: { type: Number, default: 5200 },
})

const current = ref(0)
const paused = ref(false)

const count = computed(() => props.items.length)
const canLoop = computed(() => count.value > 1)
const reducedMotion =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

let timer = null

function stop() {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function start() {
  stop()
  if (!canLoop.value || reducedMotion || paused.value || document.hidden) return
  timer = setInterval(() => {
    current.value = (current.value + 1) % count.value
  }, props.interval)
}

function pause() {
  paused.value = true
  stop()
}

function resume() {
  paused.value = false
  start()
}

function go(index) {
  if (!count.value) return
  current.value = ((index % count.value) + count.value) % count.value
  start()
}

function prev() {
  go(current.value - 1)
}

function next() {
  go(current.value + 1)
}

function onVisibilityChange() {
  if (document.hidden) stop()
  else start()
}

/** 封面缺失 / 加载失败时收起 img，露出深色底与文字，不留破图标 */
function onImageError(event) {
  event.currentTarget.hidden = true
}

watch(
  () => count.value,
  () => {
    current.value = 0
    start()
  },
)

onMounted(() => {
  start()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  stop()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div
    v-if="items.length"
    class="ink-carousel"
    role="region"
    aria-roledescription="carousel"
    aria-label="志愿活动影像"
    @mouseenter="pause"
    @mouseleave="resume"
    @focusin="pause"
    @focusout="resume"
  >
    <span class="ink-carousel-kicker" aria-hidden="true">志愿影像</span>

    <div
      class="ink-carousel-track"
      aria-live="off"
      :style="{ transform: `translateX(-${current * 100}%)` }"
    >
      <RouterLink
        v-for="(item, index) in items"
        :key="item.id"
        class="ink-carousel-slide"
        :to="`${toBase}/${item.id}`"
        :aria-hidden="index !== current"
        :tabindex="index === current ? 0 : -1"
        :aria-label="`查看活动：${item.title}`"
      >
        <img
          :src="item.cover"
          :alt="item.title"
          loading="eager"
          decoding="async"
          @error="onImageError"
        />
        <span class="ink-carousel-cap">
          <span class="ink-carousel-title">{{ item.title }}</span>
          <span class="ink-carousel-meta">
            {{ [item.type, item.org].filter(Boolean).join(' · ') }}
          </span>
        </span>
      </RouterLink>
    </div>

    <div v-if="canLoop" class="ink-carousel-ui">
      <button type="button" class="ink-carousel-arrow" aria-label="上一张" @click="prev">
        ‹
      </button>
      <span class="ink-carousel-dots">
        <button
          v-for="(item, index) in items"
          :key="`dot-${item.id}`"
          type="button"
          class="ink-carousel-dot"
          :class="{ 'is-current': index === current }"
          :aria-label="`第 ${index + 1} 张：${item.title}`"
          :aria-current="index === current ? 'true' : undefined"
          @click="go(index)"
        ></button>
      </span>
      <button type="button" class="ink-carousel-arrow" aria-label="下一张" @click="next">
        ›
      </button>
    </div>
  </div>
</template>
