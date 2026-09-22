<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getNotification, listNotifications } from '@/api/system'
import { useToast } from '@/composables/useToast'
import { useDictStore } from '@/stores/dict'

import InkButton from '@/components/common/InkButton.vue'
import InkEmpty from '@/components/common/InkEmpty.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import NoticeList from '@/components/biz/NoticeList.vue'

const route = useRoute()
const toast = useToast()
const dict = useDictStore()

const notice = ref(null)
const relatedNotices = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    // 详情接口会顺带把通知标记为已读
    notice.value = await getNotification(route.params.id)
    const data = await listNotifications({ type: notice.value.type, pageSize: 8 })
    relatedNotices.value = (data?.list || [])
      .filter((row) => row.id !== notice.value.id)
      .slice(0, 5)
  } catch (err) {
    notice.value = null
    toast.error(err.message)
  } finally {
    loading.value = false
  }
})

/** 按通知类型给出下一步入口，避免详情页成为死胡同 */
const actions = computed(() => {
  const type = notice.value?.type
  if (type === 'AUDIT') {
    return {
      hint: '审核结果类通知与你的报名、服务时长记录相关，可前往以下页面查看明细。',
      links: [
        { to: '/student/signups', label: '查看我的报名' },
        { to: '/student/durations', label: '查看我的服务时长' },
      ],
    }
  }
  if (type === 'ACTIVITY') {
    return {
      hint: '活动类通知由志愿组织发布，可浏览活动列表了解活动详情与剩余名额。',
      links: [
        { to: '/student/activities', label: '浏览志愿活动' },
        { to: '/student/signups', label: '查看我的报名' },
      ],
    }
  }
  return {
    hint: '系统通知由校团委与学生工作处发布，可查看公益画像了解自己的服务记录。',
    links: [
      { to: '/student/portrait', label: '查看我的公益画像' },
      { to: '/student/notifications', label: '返回通知列表' },
    ],
  }
})
</script>

<template>
  <div class="wrap">
    <nav class="ink-crumbs" aria-label="面包屑">
      <RouterLink to="/student/notifications">通知公告</RouterLink>
      <span class="sep" aria-hidden="true">/</span>
      <span>通知详情</span>
    </nav>

    <header class="page-head">
      <span class="hero-kicker">
        {{ notice ? dict.label('notice_type', notice.type) : '通知详情' }}
      </span>
      <h1>{{ notice?.title || '通知详情' }}</h1>
      <p v-if="notice" class="page-head-sub">
        {{ notice.from }} 发布于 {{ notice.date }}
      </p>
    </header>

    <section class="sec-detail">
      <div v-if="loading" class="panel" aria-busy="true">
        <span class="ink-skeleton ink-skeleton-row"></span>
        <span class="ink-skeleton ink-skeleton-row"></span>
        <span class="ink-skeleton ink-skeleton-row"></span>
      </div>

      <InkEmpty
        v-else-if="!notice"
        text="通知不存在或已被删除"
        hint="请返回通知列表查看其他通知"
      />

      <template v-else>
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">通知信息</span>
            <span class="panel-extra">
              <StatusTag type="notice_type" :value="notice.type" />
              <span class="ink-status" :class="notice.read ? 'tone-mute' : 'tone-warn'">
                {{ notice.read ? '已读' : '未读' }}
              </span>
            </span>
          </div>

          <dl class="ink-desc desc-one">
            <div class="ink-desc-item">
              <dt class="ink-desc-k">发布方</dt>
              <dd class="ink-desc-v">{{ notice.from }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">发布日期</dt>
              <dd class="ink-desc-v num">{{ notice.date }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">通知类型</dt>
              <dd class="ink-desc-v">{{ dict.label('notice_type', notice.type) }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">是否置顶</dt>
              <dd class="ink-desc-v">{{ notice.top ? '已置顶' : '普通通知' }}</dd>
            </div>
          </dl>
        </div>

        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">相关操作</span>
          </div>
          <p class="panel-text">{{ actions.hint }}</p>
          <div class="panel-actions">
            <InkButton v-for="link in actions.links" :key="link.to" :to="link.to" size="sm">
              {{ link.label }}
            </InkButton>
          </div>
        </div>

        <div v-if="relatedNotices.length" class="panel">
          <div class="panel-head">
            <span class="panel-title">同类型通知</span>
            <span class="panel-extra">
              <InkButton to="/student/notifications" size="sm" variant="ghost">全部通知</InkButton>
            </span>
          </div>
          <NoticeList :rows="relatedNotices" :limit="5" />
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.sec-detail {
  padding: 36px 0 72px;
}

.desc-one {
  grid-template-columns: 1fr;
}

.panel-text {
  font-size: 14px;
  line-height: 1.9;
  color: var(--c-ink-2);
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 20px;
}
</style>