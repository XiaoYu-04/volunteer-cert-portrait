<script setup>
import { computed } from 'vue'
import { formatNumber } from '@/utils/format'

/**
 * 活动卡片。对应原型的 .ink-act-card：
 * 顶部墨线、类型印章、三行键值元信息、底部报名进度条。
 */
const props = defineProps({
  activity: { type: Object, required: true },
  /** 传了就在标题上挂路由链接 */
  to: { type: [String, Object], default: null },
})

const percent = computed(() => {
  const { enrolled, capacity } = props.activity
  if (!capacity) return 0
  return Math.min(100, Math.round((enrolled / capacity) * 100))
})

const meta = computed(() => {
  const a = props.activity
  return [
    ['时间', `${a.date} ${a.time}`],
    ['地点', a.place],
    ['组织', a.org],
  ]
})
</script>

<template>
  <article class="ink-act-card">
    <div class="ink-act-top">
      <span class="ink-act-type">{{ activity.type }}</span>
      <h3 class="ink-act-title">
        <RouterLink v-if="to" :to="to">{{ activity.title }}</RouterLink>
        <template v-else>{{ activity.title }}</template>
      </h3>
    </div>

    <ul class="ink-act-meta">
      <li v-for="[k, v] in meta" :key="k" class="ink-act-meta-item">
        <span class="ink-act-meta-k">{{ k }}</span>
        <span class="ink-act-meta-v">{{ v }}</span>
      </li>
    </ul>

    <div class="ink-act-foot">
      <div
        class="ink-act-progress"
        role="img"
        :aria-label="`已报名 ${activity.enrolled} 人，名额 ${activity.capacity} 人`"
      >
        <i class="ink-act-bar" :style="{ width: `${percent}%` }"></i>
      </div>
      <span class="ink-act-count">
        <b class="ink-act-count-num">{{ formatNumber(activity.enrolled) }}</b>
        <span class="ink-act-count-cap"> / {{ formatNumber(activity.capacity) }} 人</span>
      </span>
    </div>

    <slot />
  </article>
</template>