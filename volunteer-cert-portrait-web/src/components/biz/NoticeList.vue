<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

/**
 * 通知公告列表。对应原型的 .ink-notice-item：
 * 置顶项标题用墨色加重（is-top），其余为次级墨色。
 *
 * basePath 决定标题是否可点：默认指向学生端的通知详情页；
 * 控制台里没有对应的详情路由，传空字符串即可退化为纯文本，
 * 否则点击会被路由守卫弹到 403。
 */
const props = defineProps({
  /** [{ id, title, date, from, top }] */
  rows: { type: Array, default: () => [] },
  limit: { type: Number, default: 5 },
  basePath: { type: String, default: '/student/notifications' },
})

const visible = computed(() => props.rows.slice(0, props.limit))
const linkable = computed(() => !!props.basePath)
</script>

<template>
  <ul class="ink-notice">
    <li
      v-for="row in visible"
      :key="row.id"
      class="ink-notice-item"
      :class="{ 'is-top': row.top }"
    >
      <component
        :is="linkable ? RouterLink : 'span'"
        class="ink-notice-title"
        :to="linkable ? `${basePath}/${row.id}` : undefined"
      >
        <span v-if="row.top" class="ink-notice-flag" aria-label="置顶">顶</span>{{ row.title }}
      </component>

      <span class="ink-notice-meta">
        <span class="ink-notice-date">{{ row.date }}</span>
        <span class="ink-notice-from">{{ row.from }}</span>
      </span>
    </li>
  </ul>
</template>

<style scoped>
.ink-notice-flag {
  display: inline-block;
  margin-right: 6px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 1.5;
  color: #fff;
  background: var(--c-a2);
  vertical-align: 1px;
}
</style>