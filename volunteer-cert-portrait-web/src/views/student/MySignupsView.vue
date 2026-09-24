<script setup>
import { computed, onMounted, watch } from 'vue'
import { listSignups, cancelSignup } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import InkTabs from '@/components/common/InkTabs.vue'

const toast = useToast()
const confirm = useConfirm()
const dict = useDictStore()
const user = useUserStore()

const { rows, total, loading, query, load, search } = useTable(listSignups, {
  defaultQuery: { studentId: user.info?.studentId, keyword: '', status: '' },
})

const tabs = computed(() => [
  { value: '', label: '全部' },
  ...dict.options('signup_status').map((s) => ({ value: s.value, label: s.label })),
])

/** 待审核与已通过的报名可以取消；已完成、已驳回、已取消的不可操作 */
const canCancel = (row) => row.status === 'PENDING' || row.status === 'APPROVED'

const columns = [
  { key: 'activityTitle', title: '活动' },
  { key: 'activityDate', title: '活动日期', width: '120px' },
  { key: 'activityHours', title: '服务时长', width: '100px', align: 'right' },
  { key: 'appliedAt', title: '报名时间', width: '170px' },
  { key: 'status', title: '状态', width: '110px' },
  { key: 'remark', title: '备注' },
  { key: 'actions', title: '操作', width: '180px', align: 'right' },
]

watch(
  () => query.status,
  () => search(),
)

function resetQuery() {
  query.keyword = ''
  query.status = ''
  search()
}

onMounted(() => {
  dict.load()
})

async function onCancel(row) {
  const okToGo = await confirm({
    title: '取消报名',
    message: `取消「${row.activityTitle}」后如需参加需要重新报名。`,
    confirmText: '确定取消',
    tone: 'danger',
  })
  if (!okToGo) return

  try {
    await cancelSignup(row.id)
    toast.success('已取消报名')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<template>
  <div class="wrap">
    <header class="page-head">
      <span class="hero-kicker">我的</span>
      <h1>我的报名</h1>
      <p class="page-head-sub">你提交过的全部报名申请，待审核与已通过的可以取消。</p>
    </header>

    <section class="sec-list">
      <InkTabs v-model="query.status" :tabs="tabs" />

      <form class="ink-filter" @submit.prevent="search">
        <InkField label="关键字">
          <input
            v-model.trim="query.keyword"
            class="ink-input"
            type="search"
            placeholder="活动名称"
          />
        </InkField>

        <div class="ink-filter-actions">
          <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
          <InkButton size="sm" @click="resetQuery">重置</InkButton>
        </div>
      </form>

      <InkTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty-text="还没有报名记录"
        empty-hint="在活动列表中选择活动报名"
      >
        <template #activityTitle="{ row }">
          <RouterLink class="row-link" :to="`/student/activities/${row.activityId}`">
            {{ row.activityTitle }}
          </RouterLink>
        </template>

        <template #activityDate="{ row }">
          <span class="col-num">{{ row.activityDate }}</span>
        </template>

        <template #activityHours="{ row }">
          <span class="col-num">{{ row.activityHours }} 小时</span>
        </template>

        <template #appliedAt="{ row }">
          <span class="col-num">{{ formatDateTime(row.appliedAt) }}</span>
        </template>

        <template #status="{ row }">
          <StatusTag type="signup_status" :value="row.status" />
        </template>

        <template #remark="{ row }">
          <span v-if="row.rejectReason" class="remark-bad">{{ row.rejectReason }}</span>
          <span v-else-if="row.reason" class="remark-mute">{{ row.reason }}</span>
          <span v-else class="remark-mute">—</span>
        </template>

        <template #actions="{ row }">
          <InkButton :to="`/student/activities/${row.activityId}`" size="sm" variant="ghost">
            查看活动
          </InkButton>
          <InkButton v-if="canCancel(row)" size="sm" variant="ghost" @click="onCancel(row)">
            取消报名
          </InkButton>
        </template>
      </InkTable>

      <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
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

.row-link {
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.03em;
  color: var(--c-ink);
}

.row-link:hover {
  color: var(--c-a2);
}

.remark-bad {
  font-size: 13px;
  color: var(--c-bad);
}

.remark-mute {
  font-size: 13px;
  color: var(--c-ink-3);
}
</style>
