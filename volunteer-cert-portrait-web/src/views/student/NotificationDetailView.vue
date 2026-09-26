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

/**
 * 按通知类型给出下一步入口，避免详情页成为死胡同。
 * 三类码值与后端 notification_type 字典一致：SIGNUP 报名结果 / DURATION 时长审核 / SYSTEM 系统公告
 */
const actions = computed(() => {
  const type = notice.value?.type
  if (type === 'SIGNUP') {
    return {
      hint: '可核对报名记录，或浏览其他活动。',
      links: [
        { to: '/student/signups', label: '查看我的报名' },
        { to: '/student/activities', label: '浏览志愿活动' },
      ],
    }
  }
  if (type === 'DURATION') {
    return {
      hint: '可查看服务时长记录与公益画像。',
      links: [
        { to: '/student/durations', label: '查看我的服务时长' },
        { to: '/student/portrait', label: '查看我的公益画像' },
      ],
    }
  }
  return {
    hint: '可查看我的公益画像，或返回通知列表。',
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
      <span aria-current="page">通知详情</span>
    </nav>

    <header class="page-head">
      <span class="hero-kicker">
        {{ notice ? dict.label('notification_type', notice.type) : '通知详情' }}
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
        hint="返回通知列表查看其他通知"
      />

      <template v-else>
        <div class="panel">
          <div class="panel-head">
            <h2 class="panel-title">通知信息</h2>
            <span class="panel-extra">
              <StatusTag type="notification_type" :value="notice.type" />
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
              <dd class="ink-desc-v">{{ dict.label('notification_type', notice.type) }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">是否置顶</dt>
              <dd class="ink-desc-v">{{ notice.top ? '已置顶' : '普通通知' }}</dd>
            </div>
          </dl>
        </div>

        <!-- 正文：此前这一页只渲染标题与元信息，后端 content 从没显示过。
             管理员发的公告现在会带正文（NotificationCreateDTO.content），
             留空时整块不渲染，避免出现一个空标题的空面板。 -->
        <div v-if="notice.content" v-reveal class="panel">
          <div class="panel-head">
            <h2 class="panel-title">通知正文</h2>
          </div>
          <p class="panel-text notice-content">{{ notice.content }}</p>
        </div>

        <div v-reveal class="panel">
          <div class="panel-head">
            <h2 class="panel-title">相关操作</h2>
          </div>
          <p class="panel-text">{{ actions.hint }}</p>
          <div class="panel-actions">
            <InkButton v-for="link in actions.links" :key="link.to" :to="link.to" size="sm">
              {{ link.label }}
            </InkButton>
          </div>
        </div>

        <div v-if="relatedNotices.length" v-reveal class="panel">
          <div class="panel-head">
            <h2 class="panel-title">同类型通知</h2>
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

/* 正文按管理员输入的换行原样显示，否则多段公告会被压成一行；
   再把行宽收到 52em、字号提到 15px，长公告才不至于横贯整屏读成一行 */
.notice-content {
  max-width: 52em;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 20px;
}
</style>
