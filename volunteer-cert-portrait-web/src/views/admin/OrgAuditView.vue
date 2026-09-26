<script setup>
import { onMounted, ref } from 'vue'
import { listOrgs, auditOrg, updateOrgStatus } from '@/api/org'
import { getColleges } from '@/api/auth'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDictStore } from '@/stores/dict'
import { formatNumber, formatPercent } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import InkTabs from '@/components/common/InkTabs.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const toast = useToast()
const confirm = useConfirm()
const dict = useDictStore()

const { rows, total, loading, query, search, load } = useTable(listOrgs, {
  defaultQuery: { keyword: '', status: '', college: '' },
})

// 学院下拉选项：数据源与注册页同一份（字典里 dict_type='college' 的启用项）。
// 刻意不做前端兜底列表 —— 学院是管理员可增删的字典数据，写死等于造第二份真相。
const colleges = ref([])

onMounted(async () => {
  dict.load()
  try {
    colleges.value = await getColleges()
  } catch {
    // 下拉加载失败只影响这一个筛选项，不打断页面其它数据的加载，因此不弹提示
    colleges.value = []
  }
})

const statusTabs = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'DISABLED', label: '已停用' },
]

function onTab(value) {
  query.status = value
  search()
}

const columns = [
  { key: 'name', title: '组织名称' },
  { key: 'code', title: '组织编码', width: '140px' },
  { key: 'contact', title: '负责人', width: '90px' },
  { key: 'college', title: '挂靠学院' },
  { key: 'activities', title: '累计活动', width: '100px', align: 'right' },
  { key: 'signRate', title: '签到率', width: '90px', align: 'right' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '210px', align: 'right', cellClass: 'col-actions' },
]

function resetQuery() {
  query.keyword = ''
  query.college = ''
  search()
}

/* ---------- 组织详情 ---------- */
const detail = ref({ open: false, row: null })

function openDetail(row) {
  detail.value = { open: true, row }
}

/* ---------- 资质审核 ---------- */
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
    await auditOrg(row.id, { action: mode, remark })
    toast.success(mode === 'APPROVE' ? '资质已通过' : '资质已驳回')
    dialog.value.open = false
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 停用 / 启用 ---------- */
async function toggleStatus(row) {
  const next = row.status === 'APPROVED' ? 'DISABLED' : 'APPROVED'
  if (next === 'DISABLED') {
    const okToGo = await confirm({
      title: '停用组织',
      message: `停用后「${row.name}」无法发布活动与提交时长。`,
      tone: 'danger',
      confirmText: '停用',
    })
    if (!okToGo) return
  }

  try {
    await updateOrgStatus(row.id, next)
    toast.success(next === 'APPROVED' ? '组织已启用' : '组织已停用')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<template>
  <h1 class="console-title">组织管理</h1>
  <p class="console-sub">
    审核组织资质并维护启用状态；通过后组织可发布活动、提交服务时长。
  </p>

  <section class="panel">
    <InkTabs :model-value="query.status" :tabs="statusTabs" @update:model-value="onTab" panel-id="orgs" />

    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input v-model.trim="query.keyword" class="ink-input" type="search" placeholder="组织名称" />
      </InkField>

      <InkField label="挂靠学院">
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
      <h2 class="panel-title">组织列表</h2>
      <span class="panel-extra panel-note">共 {{ formatNumber(total) }} 个组织</span>
    </div>

    <div
      role="tabpanel"
      :id="`orgs-panel-${query.status}`"
      :aria-labelledby="`orgs-tab-${query.status}`"
    >
      <InkTable :columns="columns" :rows="rows" :loading="loading" empty-text="没有符合条件的组织">
        <template #name="{ row }">
          <span class="cell-strong">{{ row.name }}</span>
        </template>

        <template #code="{ row }">
          <span class="col-num">{{ row.code }}</span>
        </template>

        <template #activities="{ row }">
          <span class="col-num">{{ row.activities }}</span>
        </template>

        <template #signRate="{ row }">
          <span class="col-num">{{ formatPercent(row.signRate) }}</span>
        </template>

        <template #status="{ row }">
          <StatusTag type="org_status" :value="row.status" />
        </template>

        <template #actions="{ row }">
          <InkButton size="sm" variant="ghost" @click="openDetail(row)">详情</InkButton>

          <template v-if="row.status === 'PENDING'">
            <InkButton size="sm" variant="ghost" @click="openAudit(row, 'APPROVE')">通过</InkButton>
            <InkButton size="sm" variant="ghost" @click="openAudit(row, 'REJECT')">驳回</InkButton>
          </template>
          <InkButton v-else-if="row.status === 'APPROVED'" size="sm" variant="ghost" @click="toggleStatus(row)">
            停用
          </InkButton>
          <InkButton v-else size="sm" variant="ghost" @click="toggleStatus(row)">启用</InkButton>
        </template>
      </InkTable>

      <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
    </div>

  </section>

  <!-- 组织详情 -->
  <InkDialog v-model="detail.open" title="组织详情" size="wide">
    <dl v-if="detail.row" class="ink-desc">
      <div class="ink-desc-item">
        <dt class="ink-desc-k">组织名称</dt>
        <dd class="ink-desc-v">{{ detail.row.name }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">组织编码</dt>
        <dd class="ink-desc-v num">{{ detail.row.code }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">负责人</dt>
        <dd class="ink-desc-v">{{ detail.row.contact }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">联系电话</dt>
        <dd class="ink-desc-v num">{{ detail.row.phone }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">挂靠学院</dt>
        <dd class="ink-desc-v">{{ detail.row.college }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">成立时间</dt>
        <dd class="ink-desc-v num">{{ detail.row.foundedAt }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">成员人数</dt>
        <dd class="ink-desc-v num">{{ formatNumber(detail.row.memberCount) }} 人</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">累计活动</dt>
        <dd class="ink-desc-v num">{{ detail.row.activities }} 场</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">签到率</dt>
        <dd class="ink-desc-v num">{{ formatPercent(detail.row.signRate) }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">审核通过率</dt>
        <dd class="ink-desc-v num">{{ formatPercent(detail.row.passRate) }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">当前状态</dt>
        <dd class="ink-desc-v">
          <StatusTag type="org_status" :value="detail.row.status" />
        </dd>
      </div>
      <div class="ink-desc-item span-2">
        <dt class="ink-desc-k">组织简介</dt>
        <dd class="ink-desc-v">{{ detail.row.intro }}</dd>
      </div>
    </dl>

    <template #footer>
      <InkButton variant="ghost" @click="detail.open = false">关闭</InkButton>
    </template>
  </InkDialog>

  <!-- 资质审核 -->
  <InkDialog
    v-model="dialog.open"
    :title="dialog.mode === 'APPROVE' ? '通过组织资质' : '驳回组织资质'"
  >
    <template v-if="dialog.row">
      <dl class="ink-desc audit-desc">
        <div class="ink-desc-item">
          <dt class="ink-desc-k">组织名称</dt>
          <dd class="ink-desc-v">{{ dialog.row.name }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">负责人</dt>
          <dd class="ink-desc-v">{{ dialog.row.contact }} · {{ dialog.row.phone }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">挂靠学院</dt>
          <dd class="ink-desc-v">{{ dialog.row.college }}</dd>
        </div>
        <div class="ink-desc-item">
          <dt class="ink-desc-k">成员人数</dt>
          <dd class="ink-desc-v num">{{ formatNumber(dialog.row.memberCount) }} 人</dd>
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
        通过后「{{ dialog.row.name }}」可发布活动与提交服务时长。
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
/* 面板标题由展示用 span 换成 h2；h2 浏览器默认加粗，这里保持原常规字重 */

.audit-field {
  margin-bottom: 0;
}
</style>
