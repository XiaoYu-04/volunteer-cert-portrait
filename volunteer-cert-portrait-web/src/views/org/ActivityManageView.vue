<script setup>
import { computed, onMounted, ref } from 'vue'
import { listActivities, updateActivityStatus, listCategories } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkProgress from '@/components/common/InkProgress.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const toast = useToast()
const confirm = useConfirm()
const dict = useDictStore()
const user = useUserStore()

const { rows, total, loading, query, search, load } = useTable(listActivities, {
  defaultQuery: {
    keyword: '',
    type: '',
    status: '',
    orgId: user.info?.orgId || 1,
  },
})

const categories = ref([])
// 走 computed 而非直接取值：dict.load() 会用接口数据整体替换字典数组
const statusOptions = computed(() => dict.options('activity_status'))

onMounted(async () => {
  dict.load()
  try {
    categories.value = await listCategories()
  } catch (err) {
    toast.error(err.message)
  }
})

const columns = [
  { key: 'title', title: '活动名称' },
  { key: 'type', title: '类型', width: '110px' },
  { key: 'date', title: '活动时间', width: '150px' },
  { key: 'place', title: '地点' },
  { key: 'progress', title: '报名 / 名额', width: '180px' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '230px', align: 'right' },
]

/** 状态流转文案，与后端 updateActivityStatus 支持的四个码一致 */
const STATUS_TEXT = { PUBLISHED: '活动已发布', CLOSED: '活动已结束', CANCELED: '活动已取消' }

/** 当前行可执行的流转：草稿可发布，已发布可结束，两者都可取消 */
function actionsOf(row) {
  const list = []
  if (row.status === 'DRAFT') list.push({ label: '发布', to: 'PUBLISHED' })
  if (row.status === 'PUBLISHED') list.push({ label: '结束', to: 'CLOSED' })
  if (row.status === 'DRAFT' || row.status === 'PUBLISHED')
    list.push({ label: '取消', to: 'CANCELED' })
  return list
}

async function changeStatus(row, status) {
  if (status === 'CANCELED') {
    const okToGo = await confirm({
      title: '取消活动',
      message: `取消「${row.title}」后学生端不再展示，已通过的报名记录仍保留。`,
      confirmText: '确认取消',
      tone: 'danger',
    })
    if (!okToGo) return
  }

  try {
    await updateActivityStatus(row.id, status)
    toast.success(STATUS_TEXT[status] || '状态已更新')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

function resetQuery() {
  query.keyword = ''
  query.type = ''
  query.status = ''
  search()
}

const hasFilter = computed(() => !!(query.keyword || query.type || query.status))
</script>

<template>
  <h1 class="console-title">活动管理</h1>
  <p class="console-sub">管理本组织发布的志愿活动，仅已发布的活动对学生开放报名。</p>

  <section class="panel">
    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input
          v-model.trim="query.keyword"
          class="ink-input"
          type="search"
          placeholder="活动名称"
        />
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
        <InkButton size="sm" :disabled="!hasFilter" @click="resetQuery">重置</InkButton>
      </div>
    </form>

    <div class="panel-head">
      <span class="panel-title">活动列表</span>
      <span class="panel-extra">
        <InkButton to="/org/activities/new" variant="primary" size="sm">发布活动</InkButton>
      </span>
    </div>

    <InkTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      empty-text="本组织暂无活动"
      empty-hint="新建活动先存为草稿，发布后才对学生开放报名"
    >
      <template #title="{ row }">
        <span class="cell-activity">
          <img
            v-if="row.cover"
            class="cell-cover"
            :src="row.cover"
            :alt="`「${row.title}」活动封面`"
            loading="lazy"
          />
          <span class="cell-strong">{{ row.title }}</span>
        </span>
      </template>

      <template #type="{ row }">
        <span class="ink-act-type">{{ row.type }}</span>
      </template>

      <template #date="{ row }">
        <span class="col-num">{{ row.date }} {{ row.time }}</span>
      </template>

      <template #progress="{ row }">
        <InkProgress :value="row.enrolled" :max="row.capacity" show-text />
      </template>

      <template #status="{ row }">
        <StatusTag type="activity_status" :value="row.status" />
      </template>

      <template #actions="{ row }">
        <template v-for="action in actionsOf(row)" :key="action.to">
          <InkButton
            size="sm"
            :variant="action.to === 'CANCELED' ? 'danger' : 'ghost'"
            @click="changeStatus(row, action.to)"
          >
            {{ action.label }}
          </InkButton>
        </template>
        <InkButton size="sm" variant="ghost" :to="`/org/signups?activityId=${row.id}`"
          >报名审核</InkButton
        >
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
  </section>
</template>

<style scoped>
.ink-filter-actions {
  display: flex;
  gap: var(--sp-3);
  padding-bottom: 2px;
}

.cell-strong {
  font-family: var(--font-display);
  color: var(--c-ink);
}

.cell-activity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cell-cover {
  flex: none;
  width: 48px;
  aspect-ratio: 3 / 2;
  object-fit: cover;
  border: 1px solid var(--c-line);
}
</style>
