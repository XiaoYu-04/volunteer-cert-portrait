<script setup>
import { useTable } from '@/composables/useTable'
import { listLogs } from '@/api/system'
import { formatNumber } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'

/**
 * 日志模块取值与后端 sys_log.module 一致，用于精确匹配筛选。
 * 接口只按模块全等匹配，因此这里用固定选项而不是从当前页数据里现取。
 */
const MODULES = ['时长认证', '志愿活动', '活动报名', '用户与权限', '签到签退', '志愿组织', '认证']

const { rows, total, loading, query, search } = useTable(listLogs, {
  defaultQuery: { keyword: '', module: '' },
})

/*
 * 列清单。
 *
 * 这里**刻意没有「操作对象」列**：后端 operation_log.target 这一列虽然存在，
 * 但写入侧从来不填（OperationLogEvent 的注释写明「留空，待 @OperationLog 增加
 * target 属性后再填」），实测 56 条日志 0 条有该键。留着这一列就是永远空白，
 * 所以先撤掉；根治要给 @OperationLog 加 target 属性并让各 Controller 传入业务对象名，
 * 已记入待办（B20-6），做完了再把这一列加回来。
 */
const columns = [
  { key: 'time', title: '操作时间', width: '160px' },
  { key: 'operator', title: '操作人', width: '100px' },
  { key: 'role', title: '角色', width: '110px' },
  { key: 'action', title: '动作', width: '110px' },
  { key: 'module', title: '模块', width: '110px' },
  { key: 'ip', title: 'IP 地址', width: '130px' },
  { key: 'result', title: '结果', width: '90px' },
]

const resultMeta = {
  SUCCESS: { label: '成功', tone: 'ok' },
  FAIL: { label: '失败', tone: 'bad' },
}

function resetQuery() {
  query.keyword = ''
  query.module = ''
  search()
}
</script>

<template>
  <h1 class="console-title">操作日志</h1>
  <p class="console-sub">
    记录管理员与组织管理员的关键操作，含审核、发布、账号变更与登录事件。日志仅可查询，不可修改或删除。
  </p>

  <section class="panel">
    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input
          v-model.trim="query.keyword"
          class="ink-input"
          type="search"
          placeholder="操作人"
        />
      </InkField>

      <InkField label="所属模块">
        <select v-model="query.module" class="ink-select">
          <option value="">全部模块</option>
          <option v-for="m in MODULES" :key="m" :value="m">{{ m }}</option>
        </select>
      </InkField>

      <div class="ink-filter-actions">
        <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
        <InkButton size="sm" @click="resetQuery">重置</InkButton>
      </div>
    </form>

    <div class="panel-head">
      <span class="panel-title">日志记录</span>
      <span class="panel-extra panel-note">共 {{ formatNumber(total) }} 条</span>
    </div>

    <InkTable :columns="columns" :rows="rows" :loading="loading" empty-text="没有符合条件的日志记录">
      <template #time="{ row }">
        <span class="col-num">{{ row.time }}</span>
      </template>

      <template #operator="{ row }">
        <span class="cell-strong">{{ row.operator }}</span>
      </template>

      <template #module="{ row }">
        <span class="module-chip">{{ row.module }}</span>
      </template>

      <template #ip="{ row }">
        <span class="col-num">{{ row.ip }}</span>
      </template>

      <template #result="{ row }">
        <span
          class="ink-status"
          :class="`tone-${(resultMeta[row.result] || {}).tone || 'mute'}`"
        >
          {{ (resultMeta[row.result] || {}).label || row.result }}
        </span>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
  </section>
</template>

<style scoped>
.panel-note {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-3);
}

.cell-strong {
  font-family: var(--font-display);
  color: var(--c-ink);
}

.module-chip {
  display: inline-block;
  padding: 2px 8px;
  font-family: var(--font-display);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-2);
  border: 1px solid var(--c-line);
  background: #fff;
  white-space: nowrap;
}

.ink-filter-actions {
  display: flex;
  gap: 12px;
  padding-bottom: 2px;
}
</style>