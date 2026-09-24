<script setup>
import { computed, onMounted, ref } from 'vue'
import { listDurations, auditDuration, batchAuditDurations, getAuditSummary } from '@/api/certification'
import { getColleges } from '@/api/auth'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDictStore } from '@/stores/dict'
import { formatDateTime } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import InkStat from '@/components/common/InkStat.vue'

const toast = useToast()
const confirm = useConfirm()
const dict = useDictStore()

const { rows, total, loading, query, search, load } = useTable(listDurations, {
  defaultQuery: { keyword: '', status: 'PENDING_AUDIT', college: '' },
})

const summary = ref(null)
const selected = ref([])

// 学院下拉选项：数据源与注册页同一份（字典里 dict_type='college' 的启用项）。
// 刻意不做前端兜底列表 —— 学院是管理员可增删的字典数据，写死等于造第二份真相。
const colleges = ref([])

/** 驳回率由三态分布算出，与通过率互补但不等于 100-通过率（还有待审核那一档） */
const rejectRate = computed(() => {
  const total = summary.value?.total || 0
  const rejected = summary.value?.items?.[2]?.value || 0
  return total ? +((rejected / total) * 100).toFixed(1) : 0
})

function resetQuery() {
  query.keyword = ''
  query.status = 'PENDING_AUDIT'
  query.college = ''
  search()
}

const columns = [
  { key: 'select', title: '', width: '44px' },
  { key: 'studentName', title: '学生', width: '120px' },
  { key: 'studentNo', title: '学号', width: '120px' },
  { key: 'college', title: '学院' },
  { key: 'activityTitle', title: '活动' },
  { key: 'hours', title: '时长', width: '90px', align: 'right' },
  { key: 'serviceDate', title: '服务日期', width: '120px' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '160px', align: 'right' },
]

const statusOptions = dict.options('duration_status')

/** 当前页可勾选的记录（已审核的不能重复处理） */
const selectable = computed(() => rows.value.filter((r) => r.status === 'PENDING_AUDIT'))
const allChecked = computed(
  () => selectable.value.length > 0 && selected.value.length === selectable.value.length,
)

function toggleAll(e) {
  selected.value = e.target.checked ? selectable.value.map((r) => r.id) : []
}

function toggleOne(id, checked) {
  if (checked) {
    if (!selected.value.includes(id)) selected.value.push(id)
  } else {
    selected.value = selected.value.filter((x) => x !== id)
  }
}

onMounted(async () => {
  dict.load()
  // 学院下拉与三态概览互不依赖，并发取：下拉慢或挂了都不该拖住概览的渲染。
  // catch 挂在 Promise 上而不是 await 外面，是为了不让它在 await 之前变成未处理拒绝。
  const collegesPromise = getColleges().catch(() => [])
  summary.value = await getAuditSummary()
  colleges.value = await collegesPromise
})

/* ---------- 单条审核 ---------- */
const dialog = ref({ open: false, mode: 'APPROVE', row: null, remark: '' })

function openAudit(row, mode) {
  dialog.value = { open: true, mode, row, remark: '' }
}

async function submitAudit() {
  const { row, mode, remark } = dialog.value
  if (mode === 'REJECT' && !remark.trim()) {
    toast.warn('驳回时必须填写理由')
    return
  }
  try {
    await auditDuration(row.id, { action: mode, remark })
    toast.success(mode === 'APPROVE' ? '已通过' : '已驳回')
    dialog.value.open = false
    selected.value = selected.value.filter((x) => x !== row.id)
    await load()
    summary.value = await getAuditSummary()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 批量审核 ---------- */
async function batchAudit() {
  if (!selected.value.length) {
    toast.warn('请先勾选要处理的记录')
    return
  }
  const okToGo = await confirm({
    title: '批量通过',
    message: `通过后选中的 ${selected.value.length} 条记录计入学生公益画像。`,
  })
  if (!okToGo) return

  try {
    const data = await batchAuditDurations({ ids: selected.value, action: 'APPROVE' })
    toast.success(`已通过 ${data.count} 条`)
    selected.value = []
    await load()
    summary.value = await getAuditSummary()
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<template>
  <h1 class="console-title">服务时长审核</h1>
  <p class="console-sub">
    审核组织提交的服务时长，驳回须填写理由。
  </p>

  <!-- 审核三态概览 -->
  <div class="stats stats-console">
    <InkStat label="审核总量（全校）" :value="summary?.total || 0" unit="条" :delta="null" />
    <InkStat label="已通过" :value="summary?.items?.[0]?.value || 0" unit="条" :delta="null" />
    <InkStat label="已驳回" :value="summary?.items?.[2]?.value || 0" unit="条" :delta="null" />
    <InkStat label="通过率" :value="+((summary?.passRate || 0) * 100).toFixed(1)" unit="%" :delta="null" />
    <InkStat label="驳回率" :value="rejectRate" unit="%" :delta="null" />
    <InkStat label="待处理队列" :value="summary?.pendingInQueue || 0" unit="条" :delta="null" />
  </div>

  <section class="panel panel-gap">
    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input v-model.trim="query.keyword" class="ink-input" type="search" placeholder="学生姓名或活动名称" />
      </InkField>

      <InkField label="状态">
        <select v-model="query.status" class="ink-select">
          <option value="">全部状态</option>
          <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </InkField>

      <InkField label="学院">
        <select v-model="query.college" class="ink-select">
          <option value="">全部学院</option>
          <option v-for="c in colleges" :key="c.value" :value="c.value">{{ c.label }}</option>
        </select>
      </InkField>

      <div class="ink-filter-actions">
        <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
        <InkButton size="sm" @click="resetQuery">重置</InkButton>
      </div>
    </form>

    <div class="panel-head">
      <span class="panel-title">时长记录</span>
      <span class="panel-extra">
        <InkButton size="sm" :disabled="!selected.length" @click="batchAudit">
          批量通过{{ selected.length ? `（${selected.length}）` : '' }}
        </InkButton>
      </span>
    </div>

    <InkTable :columns="columns" :rows="rows" :loading="loading" empty-text="没有符合条件的时长记录">
      <template #select="{ row }">
        <label v-if="row.status === 'PENDING_AUDIT'" class="ink-check">
          <input
            type="checkbox"
            :checked="selected.includes(row.id)"
            :aria-label="`选择 ${row.studentName} 的记录`"
            @change="toggleOne(row.id, $event.target.checked)"
          />
        </label>
      </template>

      <template #studentName="{ row }">
        <span class="cell-strong">{{ row.studentName }}</span>
      </template>

      <template #studentNo="{ row }">
        <span class="col-num">{{ row.studentNo }}</span>
      </template>

      <template #hours="{ row }">
        <span class="col-num">{{ row.hours }}</span>
      </template>

      <template #serviceDate="{ row }">
        <span class="col-num">{{ row.serviceDate }}</span>
      </template>

      <template #status="{ row }">
        <StatusTag type="duration_status" :value="row.status" />
      </template>

      <template #actions="{ row }">
        <template v-if="row.status === 'PENDING_AUDIT'">
          <InkButton size="sm" variant="ghost" @click="openAudit(row, 'APPROVE')">通过</InkButton>
          <InkButton size="sm" variant="ghost" @click="openAudit(row, 'REJECT')">驳回</InkButton>
        </template>
        <span v-else class="cell-mute">{{ formatDateTime(row.auditedAt) }}</span>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />

    <!-- 表头全选放在表格下方会不直观，这里用一行提示代替：勾选后点「批量通过」 -->
    <p v-if="selectable.length" class="select-hint">
      <label class="ink-check">
        <input type="checkbox" :checked="allChecked" @change="toggleAll" />
        <span>全选本页 {{ selectable.length }} 条待审核记录</span>
      </label>
    </p>
  </section>

  <!-- 审核弹窗 -->
  <InkDialog
    v-model="dialog.open"
    :title="dialog.mode === 'APPROVE' ? '通过服务时长' : '驳回服务时长'"
  >
    <template v-if="dialog.row">
      <dl class="ink-desc audit-desc">
        <div class="ink-desc-item">
          <dt class="ink-desc-k">学生</dt>
          <dd class="ink-desc-v">{{ dialog.row.studentName }}（{{ dialog.row.studentNo }}）</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">学院</dt>
          <dd class="ink-desc-v">{{ dialog.row.college }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">活动</dt>
          <dd class="ink-desc-v">{{ dialog.row.activityTitle }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">服务时长</dt>
          <dd class="ink-desc-v num">{{ dialog.row.hours }} 小时</dd>
        </div>
        <div class="ink-desc-item span-2">
          <dt class="ink-desc-k">提交组织</dt>
          <dd class="ink-desc-v">{{ dialog.row.orgName }}</dd>
        </div>
      </dl>

      <InkField
        v-if="dialog.mode === 'REJECT'"
        label="驳回理由"
        required
        class="audit-field"
      >
        <textarea
          v-model.trim="dialog.remark"
          class="ink-textarea"
          placeholder="请说明驳回原因"
        ></textarea>
      </InkField>
      <p v-else class="ink-dialog-text audit-field">
        通过后该学生的累计志愿时长增加 <b>{{ dialog.row.hours }}</b> 小时，计入公益画像。
      </p>
    </template>

    <template #footer>
      <InkButton variant="ghost" @click="dialog.open = false">取消</InkButton>
      <InkButton
        :variant="dialog.mode === 'APPROVE' ? 'primary' : 'danger'"
        @click="submitAudit"
      >
        {{ dialog.mode === 'APPROVE' ? '确认通过' : '确认驳回' }}
      </InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.stats-console {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.stats-console :deep(.ink-stat-card) {
  padding: 24px 20px;
}

.stats-console :deep(.ink-stat-card:nth-child(3n)) {
  border-right: 1px solid var(--c-line);
}

.stats-console :deep(.ink-stat-card:nth-child(6n)) {
  border-right: 0;
}

.panel-gap {
  margin-top: 36px;
}

.select-hint {
  margin-top: 16px;
  font-size: 13px;
  color: var(--c-ink-3);
}

.audit-desc {
  grid-template-columns: 1fr;
  margin-bottom: 22px;
}

.audit-field {
  margin-bottom: 0;
}

@media (max-width: 1000px) {
  .stats-console {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .stats-console :deep(.ink-stat-card:nth-child(6n)) {
    border-right: 1px solid var(--c-line);
  }
  .stats-console :deep(.ink-stat-card:nth-child(3n)) {
    border-right: 0;
  }
}

@media (max-width: 620px) {
  .stats-console {
    grid-template-columns: 1fr;
  }
  .stats-console :deep(.ink-stat-card) {
    border-right: 0 !important;
  }
}
</style>
