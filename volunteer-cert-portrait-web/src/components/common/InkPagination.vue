<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  /** 是否显示「共 N 条」 */
  showTotal: { type: Boolean, default: true },
})

const emit = defineEmits(['update:page', 'update:pageSize'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

/**
 * 页码序列，超过 7 页时用省略号收拢：1 … 4 5 6 … 20
 * 用字符串 'gap' 标记省略号，避免与真实页码冲突。
 */
const items = computed(() => {
  const last = totalPages.value
  const cur = props.page
  if (last <= 7) return range(1, last)

  const out = [1]
  const start = Math.max(2, cur - 1)
  const end = Math.min(last - 1, cur + 1)

  if (start > 2) out.push('gap')
  out.push(...range(start, end))
  if (end < last - 1) out.push('gap')
  out.push(last)
  return out
})

function range(a, b) {
  const out = []
  for (let i = a; i <= b; i++) out.push(i)
  return out
}

function go(p) {
  if (p < 1 || p > totalPages.value || p === props.page) return
  emit('update:page', p)
}

function changeSize(e) {
  emit('update:pageSize', Number(e.target.value))
  emit('update:page', 1)
}
</script>

<template>
  <div v-if="total > 0" class="ink-pager">
    <span v-if="showTotal" class="ink-pager-info">
      共 <b>{{ total }}</b> 条 · 第 {{ page }} / {{ totalPages }} 页
    </span>

    <button
      class="ink-pager-btn"
      type="button"
      :disabled="page <= 1"
      aria-label="上一页"
      @click="go(page - 1)"
    >
      ‹
    </button>

    <template v-for="(item, i) in items" :key="`${item}-${i}`">
      <span v-if="item === 'gap'" class="ink-pager-gap" aria-hidden="true">…</span>
      <button
        v-else
        class="ink-pager-btn"
        :class="{ 'is-current': item === page }"
        type="button"
        :aria-current="item === page ? 'page' : undefined"
        @click="go(item)"
      >
        {{ item }}
      </button>
    </template>

    <button
      class="ink-pager-btn"
      type="button"
      :disabled="page >= totalPages"
      aria-label="下一页"
      @click="go(page + 1)"
    >
      ›
    </button>

    <label class="ink-pager-size">
      <span class="sr-only">每页条数</span>
      <select class="ink-select" :value="pageSize" @change="changeSize">
        <option v-for="n in [10, 20, 50]" :key="n" :value="n">{{ n }} 条 / 页</option>
      </select>
    </label>
  </div>
</template>

<style scoped>
.ink-pager-size {
  margin-left: 6px;
}
.ink-pager-size .ink-select {
  width: auto;
  min-height: 34px;
  padding: 4px 32px 4px 12px;
  font-size: 12px;
  font-family: var(--font-mono);
}
</style>