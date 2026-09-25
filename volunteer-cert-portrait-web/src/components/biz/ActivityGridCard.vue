<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { formatHours, formatNumber } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

/**
 * 网格版活动卡片，用于「浏览志愿活动」页。
 * 结构对齐参考稿：封面在上 → 标题（两行截断）→ 底栏「日期 + 指标」。
 *
 * 与 ActivityCard.vue 的区别：那个是首页「本期志愿活动」的竖排键值卡片，
 * 带报名进度条；两者是并行的两套，改这个不会动到首页。
 */
const props = defineProps({
  activity: { type: Object, required: true },
  /** 详情页路由，整卡可点（拉伸链接铺在标题上） */
  to: { type: [String, Object], required: true },
})

/**
 * 无封面时的印章取色：按类型名散列到调色板，同一类型恒定同色。
 * 用散列而不是「分类 id 取模」，是因为卡片拿不到分类列表，且这样与分类增删无关。
 */
const TONES = ['--c-a2', '--c-a3', '--c-a4', '--c-ok', '--c-a5']
const tone = computed(() => {
  const name = props.activity.type || ''
  let hash = 0
  for (let i = 0; i < name.length; i += 1) hash = (hash * 31 + name.charCodeAt(i)) >>> 0
  return `var(${TONES[hash % TONES.length]})`
})

/** 印章字取类型首字：「校园服务」→「校」 */
const seal = computed(() => (props.activity.type || '志').slice(0, 1))
</script>

<template>
  <article class="ink-agc">
    <div class="ink-agc-cover">
      <img
        v-if="activity.cover"
        :src="activity.cover"
        :alt="`${activity.title}封面`"
        loading="lazy"
      />
      <div v-else class="ink-agc-fallback">
        <span class="ink-agc-seal" :style="{ '--tone': tone }" aria-hidden="true">{{ seal }}</span>
      </div>

      <span class="ink-agc-type">{{ activity.type }}</span>
      <StatusTag type="activity_status" :value="activity.status" />
    </div>

    <div class="ink-agc-body">
      <h3 class="ink-agc-title">
        <RouterLink :to="to">{{ activity.title }}</RouterLink>
      </h3>
      <p class="ink-agc-sub">{{ activity.place }} · {{ activity.org }}</p>
    </div>

    <div class="ink-agc-foot">
      <span>{{ activity.date }}</span>
      <span class="ink-agc-stats">
        <span>{{ formatHours(activity.hours) }}</span>
        <span>
          <b class="ink-agc-foot-num">{{ formatNumber(activity.enrolled) }}</b>
          / {{ formatNumber(activity.capacity) }} 人
        </span>
      </span>
    </div>
  </article>
</template>
