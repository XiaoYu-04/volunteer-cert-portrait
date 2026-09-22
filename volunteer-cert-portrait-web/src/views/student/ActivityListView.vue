<script setup>
import { onMounted, ref } from 'vue'
import { listActivities, listCategories } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useDictStore } from '@/stores/dict'
import { formatNumber } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const dict = useDictStore()

const { rows, total, loading, query, search } = useTable(listActivities, {
  defaultQuery: { keyword: '', type: '', status: 'PUBLISHED' },
})

function resetQuery() {
  query.keyword = ''
  query.type = ''
  query.status = 'PUBLISHED'
  search()
}

const categories = ref([])
onMounted(async () => {
  dict.load()
  categories.value = await listCategories()
})

const columns = [
  { key: 'title', title: '活动名称' },
  { key: 'type', title: '类型', width: '110px' },
  { key: 'date', title: '时间', width: '160px' },
  { key: 'place', title: '地点' },
  { key: 'org', title: '组织' },
  { key: 'enrolled', title: '报名 / 名额', width: '120px', align: 'right' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '110px', align: 'right' },
]

const statusOptions = dict.options('activity_status')
</script>

<template>
  <div class="wrap">
    <header class="page-head">
      <span class="hero-kicker">志愿活动</span>
      <h1>浏览志愿活动</h1>
      <p class="page-head-sub">
        按类型与关键字筛选，查看活动详情与剩余名额。报名后由组织管理员审核，结果通过站内通知送达。
      </p>
    </header>

    <section class="sec-list">
      <form class="ink-filter" @submit.prevent="search">
        <InkField label="关键字">
          <input v-model.trim="query.keyword" class="ink-input" type="search" placeholder="活动名称" />
        </InkField>

        <InkField label="活动类型">
          <select v-model="query.type" class="ink-select">
            <option value="">全部类型</option>
            <option v-for="c in categories" :key="c.id" :value="c.name">{{ c.name }}</option>
          </select>
        </InkField>

        <InkField label="状态">
          <select v-model="query.status" class="ink-select">
            <option value="">全部状态</option>
            <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </InkField>

        <div class="ink-filter-actions">
          <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
          <InkButton size="sm" @click="resetQuery">重置</InkButton>
        </div>
      </form>

      <InkTable :columns="columns" :rows="rows" :loading="loading" empty-text="没有符合条件的活动">
        <template #type="{ row }">
          <span class="ink-act-type">{{ row.type }}</span>
        </template>

        <template #date="{ row }">
          <span class="col-num">{{ row.date }} {{ row.time }}</span>
        </template>

        <template #enrolled="{ row }">
          <span class="col-num">{{ formatNumber(row.enrolled) }} / {{ formatNumber(row.capacity) }}</span>
        </template>

        <template #status="{ row }">
          <StatusTag type="activity_status" :value="row.status" />
        </template>

        <template #actions="{ row }">
          <InkButton :to="`/student/activities/${row.id}`" size="sm" variant="ghost">详情</InkButton>
        </template>
      </InkTable>

      <InkPagination
        v-model:page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
      />
    </section>
  </div>
</template>

<style scoped>
.sec-list {
  padding: 36px 0 72px;
}

.ink-filter-actions {
  display: flex;
  gap: 12px;
  padding-bottom: 2px;
}
</style>