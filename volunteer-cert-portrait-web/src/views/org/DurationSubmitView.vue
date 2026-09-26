<script setup>
import { computed, onMounted, ref } from 'vue'
import { listDurations, submitDurations } from '@/api/certification'
import { listActivities, listAttendance } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useSelection } from '@/composables/useSelection'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
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

const orgId = computed(() => user.info?.orgId || 1)

const { rows, total, loading, query, search, load } = useTable(listDurations, {
  defaultQuery: { keyword: '', status: '', orgId: orgId.value },
})

const activities = ref([])
const all = ref([])
// 走 computed 而非直接取值：dict.load() 会用接口数据整体替换字典数组
const statusOptions = computed(() => dict.options('duration_status'))

const columns = [
  { key: 'studentName', title: '学生', width: '110px' },
  { key: 'studentNo', title: '学号', width: '120px' },
  { key: 'college', title: '学院' },
  { key: 'activityTitle', title: '活动' },
  { key: 'serviceDate', title: '服务日期', width: '120px' },
  { key: 'hours', title: '时长', width: '100px', align: 'right' },
  { key: 'submittedAt', title: '提交时间', width: '150px' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'remark', title: '审核意见' },
]

/** 弹窗里的候选人表格列，与主表格区分开 */
const candidateColumns = [
  { key: 'select', title: '', width: '44px' },
  { key: 'studentName', title: '学生', width: '110px' },
  { key: 'studentNo', title: '学号', width: '120px' },
  { key: 'college', title: '学院' },
  { key: 'signOutAt', title: '签退时间', width: '150px' },
  { key: 'hours', title: '可提交时长', width: '120px', align: 'right' },
]

/** 本组织时长记录的状态分布，取全量数据统计，与列表分页无关 */
const summary = computed(() => {
  const counts = { PENDING_SUBMIT: 0, PENDING_AUDIT: 0, APPROVED: 0, REJECTED: 0 }
  all.value.forEach((item) => {
    if (counts[item.status] !== undefined) counts[item.status] += 1
  })
  return { total: all.value.length, ...counts }
})

async function loadSummary() {
  try {
    const data = await listDurations({ orgId: orgId.value, page: 1, pageSize: 500 })
    all.value = data?.list || []
  } catch (err) {
    toast.error(err.message)
  }
}

onMounted(async () => {
  dict.load()
  loadSummary()
  try {
    const data = await listActivities({ orgId: orgId.value, page: 1, pageSize: 100 })
    activities.value = data?.list || []
  } catch (err) {
    toast.error(err.message)
  }
})

function resetQuery() {
  query.keyword = ''
  query.status = ''
  search()
}

/* ---------- 提交时长 ---------- */
const dialog = ref({
  open: false,
  activityId: '',
  candidates: [],
  loading: false,
  selected: [],
  submitting: false,
})

const selectedActivity = computed(
  () => activities.value.find((a) => a.id === Number(dialog.value.activityId)) || null,
)

/** 选中集合落在弹窗对象上（打开弹窗或换活动时整体重置），套一层可写 computed 交给 useSelection */
const candidateSelected = computed({
  get: () => dialog.value.selected,
  set: (value) => {
    dialog.value.selected = value
  },
})
const {
  candidates: submittable,
  allChecked,
  toggleAll,
  toggleOne,
} = useSelection(
  computed(() => dialog.value.candidates),
  { selectable: (c) => !c.submitted, selected: candidateSelected },
)

function openSubmit() {
  dialog.value = {
    open: true,
    activityId: '',
    candidates: [],
    loading: false,
    selected: [],
    submitting: false,
  }
}

/** 候选来源：该活动已签退的签到记录，且排除已提交过时长的学生 */
async function loadCandidates() {
  const activityId = Number(dialog.value.activityId)
  dialog.value.candidates = []
  dialog.value.selected = []
  if (!activityId) return

  dialog.value.loading = true
  try {
    const [attendance, existing] = await Promise.all([
      listAttendance({ activityId, status: 'SIGNED_OUT', page: 1, pageSize: 200 }),
      listDurations({ orgId: orgId.value, page: 1, pageSize: 500 }),
    ])
    const submitted = new Set(
      (existing?.list || [])
        .filter((d) => d.activityId === activityId && d.status !== 'REJECTED')
        .map((d) => d.studentId),
    )
    dialog.value.candidates = (attendance?.list || []).map((item) => ({
      ...item,
      submitted: submitted.has(item.studentId),
    }))
  } catch (err) {
    toast.error(err.message)
  } finally {
    dialog.value.loading = false
  }
}

async function submit() {
  const activity = selectedActivity.value
  if (!activity) {
    toast.warn('请先选择活动')
    return
  }
  if (!dialog.value.selected.length) {
    toast.warn('请先勾选要提交的学生')
    return
  }

  const items = dialog.value.selected.map((id) => {
    const c = dialog.value.candidates.find((x) => x.id === id)
    return {
      studentId: c.studentId,
      activityId: c.activityId,
      activityTitle: c.activityTitle,
      activityType: activity.type,
      orgId: orgId.value,
      orgName: activity.org,
      hours: c.hours,
      serviceDate: activity.date,
    }
  })

  dialog.value.submitting = true
  try {
    const data = await submitDurations({ items })
    toast.success(`已提交 ${data?.ids?.length || items.length} 条服务时长，等待学校审核`)
    dialog.value.open = false
    await Promise.all([load(), loadSummary()])
  } catch (err) {
    toast.error(err.message)
  } finally {
    dialog.value.submitting = false
  }
}
</script>

<template>
  <h1 class="console-title">时长提交</h1>
  <p class="console-sub">按活动批量提交服务时长，交学校管理员审核。</p>

  <div class="stats stats-duration" :aria-busy="loading">
    <InkStat label="记录总数" :value="summary.total" unit="条" :delta="null" />
    <InkStat label="待审核" :value="summary.PENDING_AUDIT" unit="条" :delta="null" />
    <InkStat label="已通过" :value="summary.APPROVED" unit="条" :delta="null" />
    <InkStat label="已驳回" :value="summary.REJECTED" unit="条" :delta="null" />
  </div>

  <section class="panel">
    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input
          v-model.trim="query.keyword"
          class="ink-input"
          type="search"
          placeholder="学生姓名或活动名称"
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

    <div class="panel-head">
      <h2 class="panel-title">时长记录</h2>
      <span class="panel-extra">
        <InkButton variant="primary" size="sm" @click="openSubmit">提交时长</InkButton>
      </span>
    </div>

    <InkTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      empty-text="本组织暂无服务时长记录"
      empty-hint="活动结束后，从已签退的签到记录中批量提交"
    >
      <template #studentName="{ row }">
        <span class="cell-strong">{{ row.studentName }}</span>
      </template>

      <template #studentNo="{ row }">
        <span class="col-num">{{ row.studentNo }}</span>
      </template>

      <template #serviceDate="{ row }">
        <span class="col-num">{{ row.serviceDate }}</span>
      </template>

      <template #hours="{ row }">
        <span class="col-num">{{ formatHours(row.hours) }}</span>
      </template>

      <template #submittedAt="{ row }">
        <span class="col-num">{{ formatDateTime(row.submittedAt) }}</span>
      </template>

      <template #status="{ row }">
        <StatusTag type="duration_status" :value="row.status" />
      </template>

      <template #remark="{ row }">
        <span :class="row.status === 'REJECTED' ? 'cell-reject' : 'cell-mute'">
          {{ row.remark || (row.auditor ? `${row.auditor} 已审核` : '—') }}
        </span>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
  </section>

  <!-- 提交时长弹窗 -->
  <InkDialog v-model="dialog.open" title="提交服务时长" size="wide">
    <InkField
      label="选择活动"
      required
      hint="仅限本组织已签退的签到记录，同一活动不重复提交"
    >
      <select v-model="dialog.activityId" class="ink-select" @change="loadCandidates">
        <option value="">请选择活动</option>
        <option v-for="a in activities" :key="a.id" :value="a.id">
          {{ a.title }}（{{ a.date }}）
        </option>
      </select>
    </InkField>

    <InkTable
      :columns="candidateColumns"
      :rows="dialog.candidates"
      :loading="dialog.loading"
      empty-text="暂无可提交的签到记录"
      empty-hint="先选择活动，且该活动需有已签退、未提交时长的学生"
    >
      <template #select="{ row }">
        <label v-if="!row.submitted" class="ink-check">
          <input
            type="checkbox"
            :checked="dialog.selected.includes(row.id)"
            :aria-label="`选择 ${row.studentName}`"
            @change="toggleOne(row.id, $event.target.checked)"
          />
        </label>
        <span v-else class="cell-mute" title="该学生此活动的时长已提交过">已提交</span>
      </template>

      <template #studentName="{ row }">
        <span class="cell-strong">{{ row.studentName }}</span>
      </template>

      <template #studentNo="{ row }">
        <span class="col-num">{{ row.studentNo }}</span>
      </template>

      <template #signOutAt="{ row }">
        <span class="col-num">{{ formatDateTime(row.signOutAt) }}</span>
      </template>

      <template #hours="{ row }">
        <span class="col-num">{{ formatHours(row.hours) }}</span>
      </template>
    </InkTable>

    <p v-if="submittable.length" class="select-hint">
      <label class="ink-check">
        <input type="checkbox" :checked="allChecked" @change="toggleAll" />
        <span>全选 {{ submittable.length }} 名可提交学生</span>
      </label>
    </p>

    <template #footer>
      <InkButton variant="ghost" @click="dialog.open = false">取消</InkButton>
      <InkButton
        variant="primary"
        :disabled="!dialog.selected.length || dialog.submitting"
        @click="submit"
      >
        {{ dialog.submitting ? '提交中…' : `确认提交${dialog.selected.length ? `（${dialog.selected.length}）` : ''}` }}
      </InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.stats-duration {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

/* 指标区与首个面板之间没有现成的间距规则，这里补上 */
.stats + .panel {
  margin-top: var(--sp-7);
}

.stats-duration :deep(.ink-stat-card) {
  padding: 24px 20px;
}

/* 基类按三列收掉第 3 列的右边框，四列布局要把它还回来 */
.stats-duration :deep(.ink-stat-card:nth-child(3n)) {
  border-right: 1px solid var(--c-line);
}

.stats-duration :deep(.ink-stat-card:nth-child(4n)) {
  border-right: 0;
}

.cell-reject {
  font-size: 13px;
  color: var(--c-bad);
}

@media (max-width: 1000px) {
  .stats-duration {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .stats-duration :deep(.ink-stat-card:nth-child(4n)) {
    border-right: 1px solid var(--c-line);
  }
  .stats-duration :deep(.ink-stat-card:nth-child(2n)) {
    border-right: 0;
  }
}

@media (max-width: 620px) {
  .stats-duration {
    grid-template-columns: 1fr;
  }
  .stats-duration :deep(.ink-stat-card) {
    border-right: 0 !important;
  }
}
</style>
