<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listSignups, cancelSignup, listMyAttendance, signIn, signOut } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatHours } from '@/utils/format'

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

/* ---------- 签到记录（待办 B24）----------
   签到状态存在签到记录里，不在报名记录上，因此单独取一次并按 signupId 建索引，
   供每一行取用。数据范围由接口按登录态裁剪，这里不传 studentId。 */
const attendanceBySignup = ref(new Map())

const attendanceOf = (row) => attendanceBySignup.value.get(row.id) || null

/**
 * 一次取全（而不是跟着列表分页走）：学生的签到记录量级很小，分页取的话
 * 「我的报名」翻到第 2 页时对应记录可能不在当前页，签到按钮会时有时无。
 */
async function loadAttendance() {
  try {
    const data = await listMyAttendance({ page: 1, pageSize: 500 })
    const map = new Map()
    const list = data?.list || []
    list.forEach((item) => map.set(item.signupId, item))
    attendanceBySignup.value = map
  } catch {
    // 签到记录拉不到不该把整页打挂：报名列表照常展示，只是这一页没有签到状态与签到入口
    attendanceBySignup.value = new Map()
  }
}

/** 报名行 + 该行的签到记录。先拼好再喂给表格，模板里就不必反复调函数 */
const enrichedRows = computed(() =>
  rows.value.map((row) => ({ ...row, attendance: attendanceOf(row) })),
)

const columns = [
  { key: 'activityTitle', title: '活动' },
  { key: 'activityDate', title: '活动日期', width: '120px' },
  { key: 'activityHours', title: '服务时长', width: '100px', align: 'right' },
  { key: 'appliedAt', title: '报名时间', width: '170px' },
  { key: 'status', title: '状态', width: '110px' },
  { key: 'attendance', title: '签到', width: '160px' },
  { key: 'remark', title: '备注' },
  { key: 'actions', title: '操作', width: '220px', align: 'right', cellClass: 'col-actions' },
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
  loadAttendance()
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
    // 取消报名会连带作废签到记录（后端 invalidateBySignupId），签到列也要跟着刷新：
    // 只刷报名列表的话，这一行会残留「未签到」而真后端此时已经查不到该记录
    await Promise.all([load(), loadAttendance()])
  } catch (err) {
    toast.error(err.message)
  }
}

/**
 * 现场签到 / 签退。
 *
 * canSignIn / canSignOut 是服务端按 A2 的 30 分钟窗口算好的，前端不重复窗口常量、
 * 也不提前拦：按钮只在为真时渲染，点了被拒时直接把后端文案（如「签到尚未开放，
 * 活动开始前 30 分钟才可签到」）弹出来。
 *
 * 成功后报名列表与签到记录都要刷新 —— 签到列读的是签到记录，而按钮的可用性
 * 又来自刷新后的签到记录（签到后该行只剩「签退」）。
 */
async function onSignIn(row) {
  try {
    await signIn(row.attendance.id)
    toast.success('签到成功')
    await Promise.all([load(), loadAttendance()])
  } catch (err) {
    toast.error(err.message)
  }
}

async function onSignOut(row) {
  try {
    await signOut(row.attendance.id)
    toast.success('签退成功')
    await Promise.all([load(), loadAttendance()])
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
      <p class="page-head-sub">
        你提交过的全部报名申请，待审核与已通过的可以取消。活动开始前 30 分钟可签到，活动结束后 30
        分钟内需签退。
      </p>
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
        :rows="enrichedRows"
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

        <template #attendance="{ row }">
          <template v-if="row.attendance">
            <StatusTag type="attendance_status" :value="row.attendance.status" />
            <span v-if="row.attendance.status === 'SIGNED_OUT'" class="col-num att-hours">
              {{ formatHours(row.attendance.hours) }}
            </span>
          </template>
          <span v-else class="remark-mute">—</span>
        </template>

        <template #remark="{ row }">
          <span v-if="row.rejectReason" class="remark-bad">{{ row.rejectReason }}</span>
          <span v-else-if="row.reason" class="remark-mute">{{ row.reason }}</span>
          <span v-else class="remark-mute">—</span>
        </template>

        <template #actions="{ row }">
          <!-- 签到 / 签退按钮只在服务端给的窗口标志为真时出现：渲染一个点了必然报错的
               按钮，比不渲染更糟（学生只会看到一句「签到尚未开放」）。
               按钮文字在各行重复，补上活动名，读屏念到时才知道是哪一场 -->
          <InkButton
            v-if="row.attendance?.canSignIn"
            size="sm"
            variant="primary"
            :aria-label="`签到：${row.activityTitle}`"
            @click="onSignIn(row)"
          >
            签到
          </InkButton>
          <InkButton
            v-if="row.attendance?.canSignOut"
            size="sm"
            variant="primary"
            :aria-label="`签退：${row.activityTitle}`"
            @click="onSignOut(row)"
          >
            签退
          </InkButton>
          <InkButton
            :to="`/student/activities/${row.activityId}`"
            size="sm"
            variant="ghost"
            :aria-label="`查看活动：${row.activityTitle}`"
          >
            查看活动
          </InkButton>
          <InkButton
            v-if="canCancel(row)"
            size="sm"
            variant="ghost"
            :aria-label="`取消报名：${row.activityTitle}`"
            @click="onCancel(row)"
          >
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
  transition: color var(--t-fast) ease-out;
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

/* 签到列：状态标签后面跟实得时长，与标签拉开一点距离。
   宽度是量出来的：150px 的列减去 td 左右 padding 各 16px 只剩 118px，而
   「已签退」标签 71px + margin 8px + 「4 小时」43px = 122px > 118px，会在 1440px 下折成两行
   （浏览器实测，见 .tmp-verify/b24-signups.json）。故列宽取 160px、间距收到 6px：
   128px 内容盒 > 71 + 6 + 47（2.5 小时更宽时）≤ 128，稳定单行。 */
.att-hours {
  margin-left: 6px;
  font-size: 13px;
  color: var(--c-ink-2);
}
</style>
