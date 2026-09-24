<script setup>
import { onMounted, ref } from 'vue'
import { listDurations } from '@/api/certification'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatHours } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import InkStat from '@/components/common/InkStat.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const toast = useToast()
const dict = useDictStore()
const user = useUserStore()

const { rows, total, loading, query, search } = useTable(listDurations, {
  defaultQuery: { studentId: user.info?.studentId, keyword: '', status: '' },
})

const summary = ref({ approved: 0, pending: 0, rejected: 0 })

/**
 * 汇总需要覆盖全部记录，而列表是分页的，因此单独取一次全量。
 * 学生个人记录量级很小，一次取回不会造成压力。
 */
async function loadSummary() {
  try {
    const data = await listDurations({ studentId: user.info?.studentId, pageSize: 200 })
    const list = data?.list || []
    const sum = list
      .filter((row) => row.status === 'APPROVED')
      .reduce((acc, row) => acc + Number(row.hours || 0), 0)
    summary.value = {
      // 浮点求和会出现 42.50000000000001 这类结果，保留一位小数
      approved: Math.round(sum * 10) / 10,
      pending: list.filter((row) => row.status === 'PENDING_AUDIT').length,
      rejected: list.filter((row) => row.status === 'REJECTED').length,
    }
  } catch (err) {
    toast.error(err.message)
  }
}

onMounted(() => {
  dict.load()
  loadSummary()
})

const columns = [
  { key: 'activityTitle', title: '活动' },
  { key: 'activityType', title: '类型', width: '110px' },
  { key: 'serviceDate', title: '服务日期', width: '120px' },
  { key: 'hours', title: '时长', width: '100px', align: 'right' },
  { key: 'status', title: '状态', width: '110px' },
  { key: 'auditedAt', title: '审核时间', width: '170px' },
  { key: 'actions', title: '操作', width: '120px', align: 'right' },
]

const statusOptions = dict.options('duration_status')

function resetQuery() {
  query.keyword = ''
  query.status = ''
  search()
}

/* ---------- 驳回理由 ---------- */
const detail = ref({ open: false, row: null })

function openDetail(row) {
  detail.value = { open: true, row }
}
</script>

<template>
  <div class="wrap">
    <header class="page-head">
      <span class="hero-kicker">我的</span>
      <h1>我的服务时长</h1>
      <p class="page-head-sub">查看服务时长记录与审核状态，驳回的可查看理由。</p>
    </header>

    <section class="sec-list">
      <div class="stats">
        <InkStat label="已通过服务时长" :value="summary.approved" unit="小时" />
        <InkStat label="待审核记录" :value="summary.pending" unit="条" />
        <InkStat label="已驳回记录" :value="summary.rejected" unit="条" />
      </div>

      <form class="ink-filter filter-gap" @submit.prevent="search">
        <InkField label="关键字">
          <input
            v-model.trim="query.keyword"
            class="ink-input"
            type="search"
            placeholder="活动名称"
          />
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

      <InkTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty-text="还没有服务时长记录"
        empty-hint="参加活动并完成签到签退后，由组织提交时长记录"
      >
        <template #activityTitle="{ row }">
          <RouterLink class="row-link" :to="`/student/activities/${row.activityId}`">
            {{ row.activityTitle }}
          </RouterLink>
        </template>

        <template #activityType="{ row }">
          <span class="ink-act-type">{{ row.activityType }}</span>
        </template>

        <template #serviceDate="{ row }">
          <span class="col-num">{{ row.serviceDate }}</span>
        </template>

        <template #hours="{ row }">
          <span class="col-num">{{ formatHours(row.hours) }}</span>
        </template>

        <template #status="{ row }">
          <StatusTag type="duration_status" :value="row.status" />
        </template>

        <template #auditedAt="{ row }">
          <span v-if="row.auditedAt" class="col-num">{{ formatDateTime(row.auditedAt) }}</span>
          <span v-else class="remark-mute">—</span>
        </template>

        <template #actions="{ row }">
          <InkButton
            v-if="row.status === 'REJECTED'"
            size="sm"
            variant="ghost"
            @click="openDetail(row)"
          >
            驳回理由
          </InkButton>
          <span v-else class="remark-mute">—</span>
        </template>
      </InkTable>

      <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
    </section>
  </div>

  <InkDialog v-model="detail.open" title="驳回理由" size="narrow">
    <template v-if="detail.row">
      <dl class="ink-desc desc-one">
        <div class="ink-desc-item">
          <dt class="ink-desc-k">活动</dt>
          <dd class="ink-desc-v">{{ detail.row.activityTitle }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">服务日期</dt>
          <dd class="ink-desc-v num">{{ detail.row.serviceDate }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">服务时长</dt>
          <dd class="ink-desc-v num">{{ formatHours(detail.row.hours) }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">审核人</dt>
          <dd class="ink-desc-v">{{ detail.row.auditor || '—' }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">审核时间</dt>
          <dd class="ink-desc-v num">{{ formatDateTime(detail.row.auditedAt) }}</dd>
        </div>
      </dl>

      <p class="ink-dialog-text reject-text">
        {{ detail.row.remark || '审核方未填写驳回理由' }}
      </p>
      <p class="ink-dialog-text reject-hint">
        请核对服务日期与签到记录后联系活动组织重新提交。
      </p>
    </template>

    <template #footer>
      <InkButton variant="ghost" @click="detail.open = false">关闭</InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.sec-list {
  padding: 36px 0 72px;
}

.filter-gap {
  margin-top: 36px;
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

.remark-mute {
  font-size: 13px;
  color: var(--c-ink-3);
}

.desc-one {
  grid-template-columns: 1fr;
  margin-bottom: 22px;
}

.reject-text {
  color: var(--c-bad);
}

.reject-hint {
  margin-top: 12px;
  font-size: 13px;
  color: var(--c-ink-3);
}
</style>
