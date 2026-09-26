<script setup>
import { onMounted, watch } from 'vue'
import { listNotifications, markNotificationRead } from '@/api/system'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useDictStore } from '@/stores/dict'

import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import InkTabs from '@/components/common/InkTabs.vue'

const toast = useToast()
const dict = useDictStore()

const { rows, total, loading, query, load, search } = useTable(listNotifications, {
  defaultQuery: { keyword: '', type: '', unreadOnly: '' },
})

/** 未读筛选的取值与 mock / 后端的 unreadOnly 参数对齐 */
const tabs = [
  { value: '', label: '全部通知' },
  { value: 'true', label: '仅看未读' },
]

const columns = [
  { key: 'title', title: '通知标题' },
  { key: 'type', title: '类型', width: '120px' },
  { key: 'from', title: '发布方', width: '150px' },
  { key: 'date', title: '发布日期', width: '120px' },
  { key: 'read', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '170px', align: 'right' },
]

const typeOptions = dict.options('notification_type')

watch(
  () => query.unreadOnly,
  () => search(),
)

function resetQuery() {
  query.keyword = ''
  query.type = ''
  query.unreadOnly = ''
  search()
}

onMounted(() => {
  dict.load()
})

async function onMarkRead(row) {
  try {
    await markNotificationRead(row.id)
    toast.success('已标记为已读')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<template>
  <div class="wrap">
    <header class="page-head">
      <span class="hero-kicker">通知公告</span>
      <h1>通知公告</h1>
      <p class="page-head-sub">报名审核、时长审核与系统通知，打开详情即视为已读。</p>
    </header>

    <section class="sec-list">
      <InkTabs v-model="query.unreadOnly" :tabs="tabs" panel-id="notices" />

      <form class="ink-filter" @submit.prevent="search">
        <InkField label="关键字">
          <input
            v-model.trim="query.keyword"
            class="ink-input"
            type="search"
            placeholder="通知标题"
          />
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

      <div
        role="tabpanel"
        :id="`notices-panel-${query.unreadOnly}`"
        :aria-labelledby="`notices-tab-${query.unreadOnly}`"
      >
        <InkTable
          :columns="columns"
          :rows="rows"
          :loading="loading"
          empty-text="没有符合条件的通知"
          empty-hint="放宽关键字或类型；开着「仅看未读」时可切回「全部通知」。"
        >
          <template #title="{ row }">
            <!-- 「顶」是缩写字形：role="img" + aria-label 让读屏念「置顶」而不是孤零零一个「顶」 -->
            <RouterLink class="notice-link" :class="{ 'is-unread': !row.read }" :to="`/student/notifications/${row.id}`">
              <span v-if="row.top" class="notice-flag" role="img" aria-label="置顶">顶</span>{{ row.title }}
            </RouterLink>
          </template>

          <template #type="{ row }">
            <StatusTag type="notification_type" :value="row.type" />
          </template>

          <template #date="{ row }">
            <span class="col-num">{{ row.date }}</span>
          </template>

          <template #read="{ row }">
            <span class="ink-status" :class="row.read ? 'tone-mute' : 'tone-warn'">
              {{ row.read ? '已读' : '未读' }}
            </span>
          </template>

          <template #actions="{ row }">
            <InkButton :to="`/student/notifications/${row.id}`" size="sm" variant="ghost">
              查看
            </InkButton>
            <InkButton v-if="!row.read" size="sm" variant="ghost" @click="onMarkRead(row)">
              标记已读
            </InkButton>
          </template>
        </InkTable>

        <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
      </div>

    </section>
  </div>
</template>

<style scoped>

.notice-link {
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.03em;
  color: var(--c-ink-2);
  transition: color var(--t-fast) ease-out;
}

.notice-link:hover {
  color: var(--c-a2);
}

.notice-link.is-unread {
  color: var(--c-ink);
  font-weight: 700;
}

.notice-flag {
  display: inline-block;
  margin-right: 6px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 1.5;
  font-weight: 400;
  color: var(--c-panel);
  background: var(--c-a2);
  vertical-align: 1px;
}
</style>
