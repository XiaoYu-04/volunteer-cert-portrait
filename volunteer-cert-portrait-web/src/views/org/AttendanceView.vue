<script setup>
import { computed, onMounted, ref } from 'vue'
import { listAttendance, updateAttendance, listActivities } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'
import { useDictStore } from '@/stores/dict'
import { formatDateTime, formatHours } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const toast = useToast()
const dict = useDictStore()
const user = useUserStore()

const { rows, total, loading, query, search, load } = useTable(listAttendance, {
  defaultQuery: { keyword: '', activityId: '', status: '' },
})

const activities = ref([])
// 走 computed 而非直接取值：dict.load() 会用接口数据整体替换字典数组
const statusOptions = computed(() => dict.options('attendance_status'))

const columns = [
  { key: 'studentName', title: '学生', width: '110px' },
  { key: 'studentNo', title: '学号', width: '120px' },
  { key: 'college', title: '学院' },
  { key: 'activityTitle', title: '活动' },
  { key: 'signInAt', title: '签到时间', width: '150px' },
  { key: 'signOutAt', title: '签退时间', width: '150px' },
  { key: 'hours', title: '实得时长', width: '110px', align: 'right' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '110px', align: 'right' },
]

onMounted(async () => {
  dict.load()
  try {
    const data = await listActivities({ orgId: user.info?.orgId || 1, page: 1, pageSize: 100 })
    activities.value = data?.list || []
  } catch (err) {
    toast.error(err.message)
  }
})

function resetQuery() {
  query.keyword = ''
  query.activityId = ''
  query.status = ''
  search()
}

/* ---------- 手动修正签到记录 ---------- */
const dialog = ref({ open: false, row: null, status: '', signInAt: '', signOutAt: '' })

function openFix(row) {
  dialog.value = {
    open: true,
    row,
    status: row.status,
    // 预填到分钟：与表格列、输入框 placeholder 同一精度（接口两种格式都收）
    signInAt: row.signInAt ? formatDateTime(row.signInAt) : '',
    signOutAt: row.signOutAt ? formatDateTime(row.signOutAt) : '',
  }
}

async function submitFix() {
  const { row, status, signInAt, signOutAt } = dialog.value
  // 记为已签退却没有签退时间，等于凭空造出时长，这里挡一道
  if (status === 'SIGNED_OUT' && !signOutAt.trim()) {
    toast.warn('置为已签退时必须填写签退时间')
    return
  }

  try {
    await updateAttendance(row.id, {
      status,
      signInAt: signInAt.trim(),
      signOutAt: signOutAt.trim(),
    })
    toast.success('签到记录已更新')
    dialog.value.open = false
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<template>
  <h1 class="console-title">签到管理</h1>
  <p class="console-sub">查看本组织活动的签到签退记录，漏签可在此手动修正。</p>

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

      <InkField label="签到状态">
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
      <span class="panel-title">签到记录</span>
      <span class="panel-extra">
        <span class="panel-note">共 {{ total }} 条</span>
        <InkButton to="/org/durations" size="sm">去提交时长</InkButton>
      </span>
    </div>

    <InkTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      empty-text="没有符合条件的签到记录"
      empty-hint="学生在现场自助签到签退，记录会汇总到这里"
    >
      <template #studentName="{ row }">
        <span class="cell-strong">{{ row.studentName }}</span>
      </template>

      <template #studentNo="{ row }">
        <span class="col-num">{{ row.studentNo }}</span>
      </template>

      <template #signInAt="{ row }">
        <span class="col-num">{{ formatDateTime(row.signInAt) }}</span>
      </template>

      <template #signOutAt="{ row }">
        <span class="col-num">{{ formatDateTime(row.signOutAt) }}</span>
      </template>

      <template #hours="{ row }">
        <span class="col-num">{{ row.hours ? formatHours(row.hours) : '—' }}</span>
      </template>

      <template #status="{ row }">
        <StatusTag type="attendance_status" :value="row.status" />
      </template>

      <template #actions="{ row }">
        <InkButton size="sm" variant="ghost" @click="openFix(row)">修正</InkButton>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
  </section>

  <!-- 修正弹窗 -->
  <InkDialog v-model="dialog.open" title="修正签到记录">
    <template v-if="dialog.row">
      <dl class="ink-desc fix-desc">
        <div class="ink-desc-item">
          <dt class="ink-desc-k">学生</dt>
          <dd class="ink-desc-v">{{ dialog.row.studentName }}（{{ dialog.row.studentNo }}）</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">活动</dt>
          <dd class="ink-desc-v">{{ dialog.row.activityTitle }}</dd>
        </div>
      </dl>

      <InkField label="签到状态" required>
        <select v-model="dialog.status" class="ink-select">
          <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </InkField>

      <InkField label="签到时间" hint="格式 2025-03-22 08:45，缺省表示未签到">
        <input v-model.trim="dialog.signInAt" class="ink-input" type="text" placeholder="2025-03-22 08:45" />
      </InkField>

      <InkField label="签退时间" hint="置为「已签退」时必填">
        <input v-model.trim="dialog.signOutAt" class="ink-input" type="text" placeholder="2025-03-22 12:05" />
      </InkField>
    </template>

    <template #footer>
      <InkButton variant="ghost" @click="dialog.open = false">取消</InkButton>
      <InkButton variant="primary" @click="submitFix">保存</InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.ink-filter-actions {
  display: flex;
  gap: var(--sp-3);
  padding-bottom: 2px;
}

/* 面板右上角的计数：与学校端各列表页同一套等宽体小字 */
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

.cell-mute {
  font-size: 13px;
  color: var(--c-ink-3);
}

.fix-desc {
  grid-template-columns: 1fr;
  margin-bottom: var(--sp-5);
}
</style>
