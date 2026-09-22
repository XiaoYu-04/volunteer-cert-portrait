<script setup>
import { onMounted, ref } from 'vue'
import { listNotifications, createNotification, deleteNotification } from '@/api/system'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDictStore } from '@/stores/dict'
import { formatNumber } from '@/utils/format'

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

const { rows, total, loading, query, search, load } = useTable(listNotifications, {
  defaultQuery: { keyword: '', type: '', unreadOnly: false },
})

onMounted(() => dict.load())

const typeOptions = dict.options('notification_type')

const readTabs = [
  { value: 'ALL', label: '全部公告' },
  { value: 'UNREAD', label: '仅未读' },
]
const activeTab = ref('ALL')

function onTab(value) {
  activeTab.value = value
  query.unreadOnly = value === 'UNREAD'
  search()
}

function resetQuery() {
  query.keyword = ''
  query.type = ''
  search()
}

const columns = [
  { key: 'title', title: '标题' },
  { key: 'type', title: '类型', width: '110px' },
  { key: 'from', title: '来源', width: '160px' },
  { key: 'date', title: '发布日期', width: '120px' },
  { key: 'read', title: '阅读状态', width: '100px' },
  { key: 'actions', title: '操作', width: '100px', align: 'right', cellClass: 'col-actions' },
]

/* ---------- 发布公告 ---------- */
const dialog = ref(false)
const form = ref({ title: '', type: 'SYSTEM', from: '系统管理员', top: false })
const error = ref('')

function openCreate() {
  form.value = { title: '', type: 'SYSTEM', from: '系统管理员', top: false }
  error.value = ''
  dialog.value = true
}

async function submit() {
  if (!form.value.title.trim()) {
    error.value = '请填写公告标题'
    return
  }
  error.value = ''

  try {
    await createNotification({
      title: form.value.title.trim(),
      type: form.value.type,
      from: form.value.from.trim() || '系统管理员',
      top: form.value.top,
    })
    toast.success('公告已发布')
    dialog.value = false
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 删除 ---------- */
async function remove(row) {
  const okToGo = await confirm({
    title: '删除公告',
    message: `确定删除公告「${row.title}」吗？删除后学生端将不再显示。`,
    tone: 'danger',
    confirmText: '删除',
  })
  if (!okToGo) return

  try {
    await deleteNotification(row.id)
    toast.success('公告已删除')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<template>
  <h1 class="console-title">通知公告管理</h1>
  <p class="console-sub">
    发布面向全校的通知公告。置顶公告在学生端排在最前；发布后学生端可见，删除后立即下线。
  </p>

  <section class="panel">
    <InkTabs :model-value="activeTab" :tabs="readTabs" @update:model-value="onTab" />

    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input v-model.trim="query.keyword" class="ink-input" type="search" placeholder="公告标题" />
      </InkField>

      <InkField label="通知类型">
        <select v-model="query.type" class="ink-select">
          <option value="">全部类型</option>
          <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
      </InkField>

      <div class="ink-filter-actions">
        <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
        <InkButton size="sm" @click="resetQuery">重置</InkButton>
      </div>
    </form>

    <div class="panel-head">
      <span class="panel-title">公告列表</span>
      <span class="panel-extra">
        <span class="panel-note">共 {{ formatNumber(total) }} 条</span>
        <InkButton size="sm" variant="primary" @click="openCreate">发布公告</InkButton>
      </span>
    </div>

    <InkTable :columns="columns" :rows="rows" :loading="loading" empty-text="没有符合条件的公告">
      <template #title="{ row }">
        <span class="notice-title" :class="{ 'is-top': row.top }">
          <span v-if="row.top" class="notice-flag">顶</span>{{ row.title }}
        </span>
      </template>

      <template #type="{ row }">
        <StatusTag type="notification_type" :value="row.type" />
      </template>

      <template #from="{ row }">
        {{ row.from }}
      </template>

      <template #date="{ row }">
        <span class="col-num">{{ row.date }}</span>
      </template>

      <template #read="{ row }">
        <span class="ink-status" :class="row.read ? 'tone-mute' : 'tone-info'">
          {{ row.read ? '已读' : '未读' }}
        </span>
      </template>

      <template #actions="{ row }">
        <InkButton size="sm" variant="ghost" @click="remove(row)">删除</InkButton>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
  </section>

  <InkDialog v-model="dialog" title="发布公告">
    <form @submit.prevent="submit">
      <InkField label="公告标题" required :error="error">
        <input
          v-model.trim="form.title"
          class="ink-input"
          type="text"
          placeholder="如 关于 2025 年春季学期志愿服务时长认证工作的通知"
        />
      </InkField>

      <InkField label="通知类型" hint="审核结果类通知会与时长审核流程联动">
        <select v-model="form.type" class="ink-select">
          <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
      </InkField>

      <InkField label="发布来源">
        <input v-model.trim="form.from" class="ink-input" type="text" placeholder="如 校团委" />
      </InkField>

      <InkField label="置顶">
        <label class="ink-check">
          <input v-model="form.top" type="checkbox" />
          <span>置顶显示在学生端公告列表最前</span>
        </label>
      </InkField>
    </form>

    <template #footer>
      <InkButton variant="ghost" @click="dialog = false">取消</InkButton>
      <InkButton variant="primary" @click="submit">确认发布</InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.panel-note {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-3);
  align-self: center;
}

.notice-title {
  color: var(--c-ink-2);
}

.notice-title.is-top {
  color: var(--c-ink);
  font-family: var(--font-display);
}

.notice-flag {
  display: inline-block;
  margin-right: 6px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 1.5;
  color: #fff;
  background: var(--c-a2);
  vertical-align: 1px;
}

.ink-filter-actions {
  display: flex;
  gap: 12px;
  padding-bottom: 2px;
}
</style>