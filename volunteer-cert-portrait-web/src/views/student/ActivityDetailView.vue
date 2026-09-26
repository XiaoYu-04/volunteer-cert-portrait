<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getActivity, listSignups, createSignup } from '@/api/volunteer'
import { useToast } from '@/composables/useToast'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatHours, formatNumber } from '@/utils/format'

import InkField from '@/components/common/InkField.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import InkEmpty from '@/components/common/InkEmpty.vue'
import InkProgress from '@/components/common/InkProgress.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const route = useRoute()
const toast = useToast()
const dict = useDictStore()
const user = useUserStore()

const activity = ref(null)
/** 当前学生在本活动的报名记录（已取消的不算） */
const mySignup = ref(null)
const loading = ref(true)

const remain = computed(() =>
  Math.max(0, (activity.value?.capacity || 0) - (activity.value?.enrolled || 0)),
)
const signed = computed(() => !!mySignup.value)
const isFull = computed(() => !!activity.value && remain.value <= 0)
const isOpen = computed(() => activity.value?.status === 'PUBLISHED')
const canSignup = computed(() => !loading.value && !signed.value && isOpen.value && !isFull.value)

/** 按钮不可点时的原因，避免出现「点了没反应」的按钮 */
const disabledReason = computed(() => {
  if (!activity.value || canSignup.value) return ''
  if (signed.value) {
    return `你已报名该活动，当前状态：${dict.label('signup_status', mySignup.value.status)}`
  }
  if (!isOpen.value)
    return `活动${dict.label('activity_status', activity.value.status)}，不再接受报名`
  if (isFull.value) return '名额已满，可浏览同类型的其他活动'
  return ''
})

async function load() {
  loading.value = true
  try {
    activity.value = await getActivity(route.params.id)
    const data = await listSignups({
      studentId: user.info?.studentId,
      activityId: route.params.id,
      pageSize: 50,
    })
    // 已取消的记录不算「已报名」，学生可以重新报名
    mySignup.value = (data?.list || []).find((s) => s.status !== 'CANCELED') || null
  } catch (err) {
    activity.value = null
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  dict.load()
  load()
})

const dialogOpen = ref(false)
const reason = ref('')
const submitting = ref(false)

function openSignup() {
  reason.value = ''
  dialogOpen.value = true
}

async function submitSignup() {
  submitting.value = true
  try {
    await createSignup({
      activityId: activity.value.id,
      studentId: user.info?.studentId,
      reason: reason.value,
    })
    toast.success('报名已提交，等待组织审核')
    dialogOpen.value = false
    await load()
  } catch (err) {
    toast.error(err.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <nav class="ink-crumbs" aria-label="面包屑">
      <RouterLink to="/student/activities">志愿活动</RouterLink>
      <span class="sep" aria-hidden="true">/</span>
      <span aria-current="page">{{ activity?.title || '活动详情' }}</span>
    </nav>

    <header class="page-head">
      <span class="hero-kicker">{{ activity?.type || '活动详情' }}</span>
      <h1>{{ activity?.title || '活动详情' }}</h1>
      <p class="page-head-sub">
        {{ activity?.description || '正在加载活动信息……' }}
      </p>
      <div class="page-head-actions">
        <InkButton v-if="canSignup" variant="primary" @click="openSignup">报名参加</InkButton>
        <InkButton v-else disabled>报名参加</InkButton>
        <InkButton to="/student/signups">我的报名</InkButton>
      </div>
      <p v-if="disabledReason" class="signup-tip">{{ disabledReason }}</p>
    </header>

    <figure v-if="!loading && activity?.cover" class="activity-cover">
      <img :src="activity.cover" :alt="`${activity.title}活动封面`" />
    </figure>

    <section class="sec-detail">
      <div v-if="loading" class="panel" aria-busy="true">
        <span class="ink-skeleton ink-skeleton-row"></span>
        <span class="ink-skeleton ink-skeleton-row"></span>
        <span class="ink-skeleton ink-skeleton-row"></span>
      </div>

      <InkEmpty v-else-if="!activity" text="活动不存在或已下线" hint="返回活动列表查看其他活动" />

      <div v-else class="grid-2">
        <div class="panel">
          <div class="panel-head">
            <h2 class="panel-title">活动信息</h2>
            <span class="panel-extra">
              <StatusTag type="activity_status" :value="activity.status" />
            </span>
          </div>

          <dl class="ink-desc desc-one">
            <div class="ink-desc-item">
              <dt class="ink-desc-k">时间</dt>
              <dd class="ink-desc-v num">{{ activity.date }} {{ activity.time }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">地点</dt>
              <dd class="ink-desc-v">{{ activity.place }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">组织</dt>
              <dd class="ink-desc-v">{{ activity.org }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">服务时长</dt>
              <dd class="ink-desc-v num">{{ formatHours(activity.hours) }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">报名截止</dt>
              <dd class="ink-desc-v num">{{ activity.deadline || '—' }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">联系人</dt>
              <dd class="ink-desc-v">{{ activity.contact || '—' }}</dd>
            </div>
          </dl>
        </div>

        <div class="panel">
          <div class="panel-head">
            <h2 class="panel-title">报名情况</h2>
          </div>

          <!-- 已报名/名额两个数由 InkProgress 内部的 aria-* 读出，这里只给含义 -->
          <InkProgress
            :value="activity.enrolled"
            :max="activity.capacity"
            show-text
            label="报名进度"
          />
          <p class="signup-remain">
            剩余名额 <b class="num">{{ formatNumber(remain) }}</b> 个 · 计划招募
            <span class="num">{{ formatNumber(activity.capacity) }}</span> 人
          </p>

          <template v-if="mySignup">
            <h3 class="sub-head">我的报名</h3>
            <p class="signup-state">
              <StatusTag type="signup_status" :value="mySignup.status" />
              <span class="num signup-time">{{ formatDateTime(mySignup.appliedAt) }}</span>
            </p>
            <p v-if="mySignup.reason" class="signup-note">报名理由：{{ mySignup.reason }}</p>
            <p v-if="mySignup.rejectReason" class="signup-reject">
              驳回理由：{{ mySignup.rejectReason }}
            </p>
          </template>
          <p v-else class="signup-none">你还没有报名本活动。</p>
        </div>
      </div>

      <section v-if="!loading && activity?.images?.length" v-reveal class="activity-gallery">
        <div class="gallery-head">
          <h2>活动图集</h2>
          <span class="num">{{ activity.images.length }} 张</span>
        </div>
        <div class="gallery-grid" :class="{ 'anim-stagger': activity.images.length > 1 }">
          <figure v-for="(image, index) in activity.images" :key="image.id || image.fileUrl">
            <img
              :src="image.fileUrl"
              :alt="image.caption || `${activity.title}图片 ${index + 1}`"
              loading="lazy"
            />
            <figcaption>
              {{ image.caption || image.fileName || `活动图片 ${index + 1}` }}
            </figcaption>
          </figure>
        </div>
      </section>
    </section>
  </div>

  <InkDialog v-model="dialogOpen" title="报名参加活动" size="narrow">
    <template v-if="activity">
      <p class="ink-dialog-text">{{ activity.title }}</p>
      <p class="ink-dialog-text dialog-meta">
        {{ activity.date }} {{ activity.time }} · {{ activity.place }} ·
        {{ formatHours(activity.hours) }}
      </p>

      <InkField label="报名理由" hint="可选">
        <textarea
          v-model.trim="reason"
          class="ink-textarea"
          placeholder="如 参与社区敬老服务"
        ></textarea>
      </InkField>
    </template>

    <template #footer>
      <InkButton variant="ghost" @click="dialogOpen = false">取消</InkButton>
      <InkButton variant="primary" :disabled="submitting" @click="submitSignup">
        {{ submitting ? '提交中…' : '提交报名' }}
      </InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.sec-detail {
  padding: 36px 0 72px;
}

.activity-cover {
  border-bottom: 1px solid var(--c-ink);
  background: var(--c-line-2);
}

.activity-cover img {
  width: 100%;
  max-height: 520px;
  object-fit: cover;
}

.signup-tip {
  margin-top: 14px;
  font-size: 13px;
  color: var(--c-ink-3);
}

.desc-one {
  grid-template-columns: 1fr;
}

.signup-remain {
  margin-top: 14px;
  font-size: 13px;
  color: var(--c-ink-3);
}

/* 剩余名额是这一块最该被先读到的数字，别和说明文字同色 */
.signup-remain b {
  color: var(--c-ink);
}

.sub-head {
  margin-top: 30px;
  padding-top: 18px;
  border-top: 1px solid var(--c-line-2);
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: 0.06em;
  color: var(--c-ink);
}

.signup-state {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}

.signup-time {
  font-size: 13px;
  color: var(--c-ink-3);
}

.signup-note,
.signup-none {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--c-ink-3);
}

.signup-reject {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--c-bad);
}

.dialog-meta {
  margin: 8px 0 24px;
  font-size: 13px;
  color: var(--c-ink-3);
}

.activity-gallery {
  margin-top: 56px;
}

.gallery-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--c-ink);
}

.gallery-head h2 {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 400;
  letter-spacing: 0.06em;
}

.gallery-head span {
  font-size: 12px;
  color: var(--c-ink-3);
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 28px 24px;
  margin-top: 24px;
}

.gallery-grid figure {
  min-width: 0;
}

.gallery-grid img {
  width: 100%;
  aspect-ratio: 3 / 2;
  object-fit: cover;
  border: 1px solid var(--c-line);
  background: var(--c-line-2);
}

.gallery-grid figcaption {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--c-ink-2);
}

@media (max-width: 760px) {
  .gallery-grid {
    grid-template-columns: 1fr;
  }
}
</style>
