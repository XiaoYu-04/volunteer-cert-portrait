<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { listSignups, auditSignup, listActivities } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import { formatDateTime } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const route = useRoute()
const toast = useToast()
const confirm = useConfirm()
const dict = useDictStore()
const user = useUserStore()

const { rows, total, loading, query, search, load } = useTable(listSignups, {
  defaultQuery: {
    keyword: '',
    status: 'PENDING',
    // 从活动管理页的「报名」入口跳进来时带上活动 id，直接筛出该活动的报名
    activityId: route.query.activityId || '',
    orgId: user.info?.orgId || 1,
  },
})

const activities = ref([])
const selected = ref([])
// 走 computed 而非直接取值：dict.load() 会用接口数据整体替换字典数组
const statusOptions = computed(() => dict.options('signup_status'))

const columns = [
  { key: 'select', title: '', width: '44px' },
  { key: 'studentName', title: '学生', width: '110px' },
  { key: 'studentNo', title: '学号', width: '120px' },
  { key: 'college', title: '学院' },
  { key: 'activityTitle', title: '活动' },
  { key: 'appliedAt', title: '报名时间', width: '150px' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '160px', align: 'right' },
]

/** 待审核的报名才能操作，已审核的不能重复处理 */
const selectable = computed(() => rows.value.filter((r) => r.status === 'PENDING'))
const allChecked = computed(
  () => selectable.value.length > 0 && selected.value.length === selectable.value.length,
)

onMounted(async () => {
  dict.load()
  try {
    const data = await listActivities({ orgId: user.info?.orgId || 1, page: 1, pageSize: 100 })
    activities.value = data?.list || []
  } catch (err) {
    toast.error(err.message)
  }
})

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

function resetQuery() {
  query.keyword = ''
  query.status = 'PENDING'
  query.activityId = ''
  selected.value = []
  search()
}

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
    await auditSignup(row.id, { action: mode, remark: remark.trim() })
    toast.success(mode === 'APPROVE' ? '报名已通过' : '报名已驳回')
    dialog.value.open = false
    selected.value = selected.value.filter((x) => x !== row.id)
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 批量通过 ---------- */
async function batchApprove() {
  if (!selected.value.length) {
    toast.warn('请先勾选要处理的报名')
    return
  }

  const okToGo = await confirm({
    title: '批量通过报名',
    message: `通过后学生将收到通知，可参加现场签到。`,
  })
  if (!okToGo) return

  // 逐条调用审核接口：单条失败（如已被审核）不影响其余记录
  const results = await Promise.allSettled(
    selected.value.map((id) => auditSignup(id, { action: 'APPROVE' })),
  )
  const done = results.filter((r) => r.status === 'fulfilled').length
  const failed = results.length - done

  if (done) toast.success(`已通过 ${done} 条`)
  if (failed) toast.warn(`${failed} 条未能通过，可能已被审核`)
  selected.value = []
  await load()
}
</script>

<template>
  <h1 class="console-title">报名审核</h1>
  <p class="console-sub">审核本组织活动收到的报名申请，驳回须填写理由。</p>

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

      <InkField label="活动">
        <select v-model="query.activityId" class="ink-select">
          <option value="">全部活动</option>
          <option v-for="a in activities" :key="a.id" :value="a.id">{{ a.title }}</option>
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

    <div class="panel-head">
      <h2 class="panel-title">报名记录</h2>
      <span class="panel-extra">
        <span class="panel-note">共 {{ total }} 条</span>
        <InkButton size="sm" :disabled="!selected.length" @click="batchApprove">
          批量通过{{ selected.length ? `（${selected.length}）` : '' }}
        </InkButton>
      </span>
    </div>

    <InkTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      empty-text="没有符合条件的报名记录"
      empty-hint="换一个关键字或状态再试"
    >
      <template #select="{ row }">
        <label v-if="row.status === 'PENDING'" class="ink-check">
          <input
            type="checkbox"
            :checked="selected.includes(row.id)"
            :aria-label="`选择 ${row.studentName} 的报名`"
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

      <template #appliedAt="{ row }">
        <span class="col-num">{{ formatDateTime(row.appliedAt) }}</span>
      </template>

      <template #status="{ row }">
        <StatusTag type="signup_status" :value="row.status" />
      </template>

      <template #actions="{ row }">
        <template v-if="row.status === 'PENDING'">
          <InkButton size="sm" variant="ghost" @click="openAudit(row, 'APPROVE')">通过</InkButton>
          <InkButton size="sm" variant="ghost" @click="openAudit(row, 'REJECT')">驳回</InkButton>
        </template>
        <span v-else-if="row.rejectReason" class="cell-mute">{{ row.rejectReason }}</span>
        <span v-else class="cell-mute">—</span>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />

    <p v-if="selectable.length" class="select-hint">
      <label class="ink-check">
        <input type="checkbox" :checked="allChecked" @change="toggleAll" />
        <span>全选本页 {{ selectable.length }} 条待审核报名</span>
      </label>
    </p>
  </section>

  <!-- 审核弹窗 -->
  <InkDialog
    v-model="dialog.open"
    :title="dialog.mode === 'APPROVE' ? '通过报名' : '驳回报名'"
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
        <div class="ink-desc-item span-2">
          <dt class="ink-desc-k">活动</dt>
          <dd class="ink-desc-v">
            {{ dialog.row.activityTitle }}（{{ dialog.row.activityDate }} · {{ dialog.row.activityHours }} 小时）
          </dd>
        </div>
        <div class="ink-desc-item span-2">
          <dt class="ink-desc-k">报名时间</dt>
          <dd class="ink-desc-v num">{{ formatDateTime(dialog.row.appliedAt) }}</dd>
        </div>
        <div class="ink-desc-item span-2">
          <dt class="ink-desc-k">报名理由</dt>
          <dd class="ink-desc-v">{{ dialog.row.reason || '学生未填写' }}</dd>
        </div>
      </dl>

      <InkField
        v-if="dialog.mode === 'REJECT'"
        label="驳回理由"
        required
        hint="理由会展示在学生的「我的报名」里"
        class="audit-field"
      >
        <textarea
          v-model.trim="dialog.remark"
          class="ink-textarea"
          placeholder="请说明驳回原因"
        ></textarea>
      </InkField>
      <p v-else class="ink-dialog-text audit-field">通过后占用 1 个名额，可在活动现场签到。</p>
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

/* 面板右上角的计数：与学校端各列表页同一套等宽体小字 */

/* 操作列整格带 .col-num（等宽体），驳回理由与占位符是中文说明，还原正文字体 */
.ink-table td.col-num .cell-mute {
  font-family: var(--font-body);
}

.audit-field {
  margin-bottom: 0;
}
</style>
