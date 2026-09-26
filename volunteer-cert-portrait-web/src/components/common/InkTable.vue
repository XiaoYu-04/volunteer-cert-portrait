<script setup>
import InkEmpty from './InkEmpty.vue'

/**
 * 墨线表格。列定义驱动，每列可具名插槽自定义单元格。
 *
 * columns: [{ key, title, width, align, cellClass }]
 * 单元格默认渲染 row[key]；用 <template #status="{ row }"> 覆盖。
 */
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' },
  emptyHint: { type: String, default: '' },
})

const widthStyle = (col) => (col.width ? { width: col.width } : undefined)
</script>

<template>
  <div class="ink-table-wrap" :aria-busy="loading ? 'true' : undefined">
    <table class="ink-table">
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            :style="widthStyle(col)"
            :class="{ 'col-num': col.align === 'right' }"
          >
            {{ col.title }}
          </th>
        </tr>
      </thead>
      <tbody>
        <template v-if="loading">
          <!-- 骨架屏是纯视觉占位：对读屏隐藏，加载状态由外层的 aria-busy 表达 -->
          <tr v-for="n in 4" :key="`sk-${n}`" aria-hidden="true">
            <td v-for="col in columns" :key="col.key">
              <span class="ink-skeleton ink-skeleton-row"></span>
            </td>
          </tr>
        </template>
        <template v-else>
          <tr v-for="row in rows" :key="row[rowKey]">
            <td
              v-for="col in columns"
              :key="col.key"
              :class="[col.cellClass, col.align === 'right' && 'col-num']"
              :style="col.align ? { textAlign: col.align } : undefined"
            >
              <slot :name="col.key" :row="row" :value="row[col.key]">{{ row[col.key] }}</slot>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>

  <InkEmpty v-if="!loading && !rows.length" :text="emptyText" :hint="emptyHint" />
</template>

<style scoped>
.ink-table-wrap {
  border-bottom: 1px solid var(--c-line-2);
}
</style>