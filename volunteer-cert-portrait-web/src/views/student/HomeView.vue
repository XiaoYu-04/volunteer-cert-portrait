<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '@/api/analytics'
import { listActivities } from '@/api/volunteer'
import { useToast } from '@/composables/useToast'
import { useDashboardText } from '@/composables/useDashboardText'
import { formatNumber } from '@/utils/format'

import InkSection from '@/components/common/InkSection.vue'
import InkStat from '@/components/common/InkStat.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkChart from '@/components/charts/InkChart.vue'
import ActivityCard from '@/components/biz/ActivityCard.vue'
import ActivityCarousel from '@/components/biz/ActivityCarousel.vue'
import ActivityGallery from '@/components/biz/ActivityGallery.vue'
import PortraitSeal from '@/components/biz/PortraitSeal.vue'
import RankList from '@/components/biz/RankList.vue'
import OrgList from '@/components/biz/OrgList.vue'
import NoticeList from '@/components/biz/NoticeList.vue'
import * as charts from '@/components/charts/options'

const toast = useToast()
const data = ref(null)
const loading = ref(true)
const photoActivities = ref([])
const carouselLoading = ref(true)

/** 轮播只取前 6 张；照片墙最多展示 10 张（真后端演示库有 10 场活动封面） */
const carouselItems = computed(() => photoActivities.value.slice(0, 6))

/**
 * 照片墙把轮播没用到的后 4 张转到最前面，避免一进「志愿风采」
 * 看到的又是轮播里那 6 张；mock 只有 6 张时保持原顺序。
 */
const galleryItems = computed(() => {
  const items = photoActivities.value
  if (items.length <= 6) return items
  return [...items.slice(6), ...items.slice(0, 6)]
})

onMounted(async () => {
  // 看板与活动封面并行取：轮播 / 照片墙只是装饰性内容，失败时静默隐藏，不能拖垮首页主体
  const [dashboard, activities] = await Promise.allSettled([
    getDashboard(),
    listActivities({ page: 1, pageSize: 12 }),
  ])

  if (dashboard.status === 'fulfilled') {
    data.value = dashboard.value
  } else {
    toast.error(dashboard.reason?.message || '数据加载失败')
  }

  if (activities.status === 'fulfilled') {
    photoActivities.value = (activities.value?.list || [])
      .filter((item) => item.cover && item.status !== 'CANCELED')
      .slice(0, 10)
  }

  loading.value = false
  carouselLoading.value = false
})

/** 图表 option 只在数据就绪后计算，避免空数据下渲染出空图 */
const trendOption = computed(() => (data.value ? charts.trend(data.value.trend) : null))
const typePieOption = computed(() => (data.value ? charts.typePie(data.value.types) : null))
const collegeOption = computed(() => (data.value ? charts.college(data.value.colleges) : null))

const topActivities = computed(() => (data.value?.activities || []).slice(0, 6))
const topColleges = computed(() => (data.value?.colleges || []).slice(0, 8))

/* 图注与无障碍描述一律从接口数据现算，见 composables/useDashboardText.js 的说明 */
const {
  trendRange,
  trendTotal,
  trendLabel,
  typeLabel,
  typeCaption,
  collegeLabel,
} = useDashboardText(data)
</script>

<template>
  <!-- ==================== Hero ==================== -->
  <section class="hero hero-compact">
    <div class="wrap hero-in">
      <div class="hero-main">
        <p class="hero-kicker">{{ trendRange || '志愿服务数据' }}</p>
        <h1>全校志愿服务<br /><em>时长认证</em>与公益画像</h1>
        <p class="hero-sub">全校志愿服务的汇总数据，含活动、签到、时长与画像。</p>
        <div class="hero-actions">
          <InkButton to="/student/activities" variant="primary">浏览志愿活动</InkButton>
          <InkButton to="/student/portrait">查看我的公益画像</InkButton>
        </div>

        <!-- 按钮下方的「按类型浏览」：把 Hero 左栏的空白补成可用的分类入口。
             status 传空串，让列表页默认的「仅已发布」放开，按类型能看到全部非草稿活动。 -->
        <div v-if="data?.types?.length" class="hero-types">
          <span class="hero-types-label">按类型浏览</span>
          <RouterLink
            v-for="item in data.types"
            :key="item.name"
            :to="{ path: '/student/activities', query: { type: item.name, status: '' } }"
          >
            {{ item.name }}<b>{{ item.value }}</b>
          </RouterLink>
        </div>
      </div>
      <div class="hero-side">
        <ActivityCarousel v-if="carouselItems.length" :items="carouselItems" />
        <div v-else-if="carouselLoading" class="ink-carousel ink-skeleton" aria-hidden="true"></div>
        <span v-else class="vtitle" aria-hidden="true">志愿服务数据</span>
      </div>
    </div>
  </section>

  <div class="wrap home-compact">
    <!-- ==================== 核心指标 ==================== -->
    <InkSection v-reveal>
      <div class="stats anim-stagger" :aria-busy="loading">
        <InkStat
          v-for="item in data?.stats || []"
          :key="item.key"
          :label="item.label"
          :value="item.value"
          :unit="item.unit"
          :delta="item.delta"
          :trend="item.trend"
        />
      </div>
    </InkSection>

    <!-- ==================== 数据图表 ==================== -->
    <InkSection v-reveal.fade no="01 / 数据" title="全年志愿服务概览">
      <div class="grid-2">
        <figure class="fig-frame">
          <InkChart
            :option="trendOption"
            :loading="loading"
            height="280px"
            :label="trendLabel"
          />
          <figcaption class="fig-cap">
            <b>图一</b>逐月活动数量（场），近 12 个月合计 {{ formatNumber(trendTotal) }} 场。
          </figcaption>
        </figure>

        <figure class="fig-frame">
          <InkChart
            :option="typePieOption"
            :loading="loading"
            height="280px"
            :label="typeLabel"
          />
          <figcaption class="fig-cap">
            <b>图二</b>活动类型结构（场），{{ typeCaption }}。
          </figcaption>
        </figure>
      </div>
    </InkSection>

    <!-- ==================== 近期活动 ==================== -->
    <InkSection v-reveal no="02 / 活动" title="本期志愿活动">
      <div class="grid-3 anim-stagger">
        <ActivityCard
          v-for="activity in topActivities"
          :key="activity.id"
          :activity="activity"
          :to="`/student/activities/${activity.id}`"
        />
      </div>
      <div class="sec-more">
        <InkButton to="/student/activities" size="sm">查看全部活动</InkButton>
      </div>
    </InkSection>

    <!-- ==================== 志愿风采 ==================== -->
    <InkSection
      v-reveal
      no="03 / 风采"
      title="志愿风采"
      desc="来自志愿活动现场的影像记录。"
    >
      <ActivityGallery :items="galleryItems" />
    </InkSection>

    <!-- ==================== 公益画像 ==================== -->
    <InkSection v-reveal no="04 / 画像" title="学生公益画像">
      <PortraitSeal :items="data?.profiles || []" />
    </InkSection>

    <!-- ==================== 流程 ==================== -->
    <InkSection v-reveal no="05 / 流程" title="志愿服务流程">
      <ol class="ink-flow anim-stagger">
        <li v-for="(step, i) in data?.flow || []" :key="step.step" class="ink-flow-step">
          <span class="ink-flow-no num">{{ i + 1 }}</span>
          <span class="ink-flow-name">{{ step.step }}</span>
          <span class="ink-flow-desc">{{ step.desc }}</span>
        </li>
      </ol>
    </InkSection>

    <!-- ==================== 排行 + 组织 + 公告 ==================== -->
    <InkSection v-reveal.fade no="06 / 排行" title="学院志愿时长排行">
      <div class="grid-2">
        <div>
          <figure class="fig-frame fig-rank">
            <InkChart
              :option="collegeOption"
              :loading="loading"
              height="240px"
              :label="collegeLabel"
            />
            <figcaption class="fig-cap"><b>图三</b>各学院累计认证志愿时长（小时）。</figcaption>
          </figure>
          <RankList :rows="topColleges" />
        </div>

        <div>
          <h3 class="sub-head">组织活跃度</h3>
          <!-- 组织名可点：跳到志愿活动列表并把组织名填进关键字（关键字同时匹配活动名与组织名） -->
          <OrgList :rows="data?.orgs || []" link-base="/student/activities" />

          <h3 class="sub-head sub-head-gap">通知公告</h3>
          <NoticeList :rows="data?.notices || []" :limit="5" />
        </div>
      </div>
    </InkSection>
  </div>
</template>

<style scoped>
.sub-head {
  font-family: var(--font-display);
  font-size: 18px;
  letter-spacing: 0.06em;
  color: var(--c-ink);
  margin-bottom: 6px;
}

.sub-head-gap {
  margin-top: 32px;
}

/* 「图三」与紧接其下的学院排行是一组，和上方图表拉开一档间距 */
.fig-rank {
  margin-bottom: 24px;
}

/* 首页竖排活动卡（ActivityCard）的标题是整张卡唯一的入口，
   组件内部没有给悬停反馈，这里补上与网格卡 / 通知列表一致的朱砂色 */
.wrap :deep(.ink-act-title a) {
  transition: color var(--t-fast) ease-out;
}

.wrap :deep(.ink-act-title a:hover) {
  color: var(--c-a2);
}

.sec-more {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

/* 首页整体比通用区块更紧凑：用户反馈「太空」，收一档留白，
   不动 ink.css 的全局 88px 节奏，避免影响其它页面。 */
.hero-compact {
  padding: 64px 0 48px;
}

.hero-compact h1 {
  margin-top: 16px;
}

.hero-compact .hero-sub {
  margin-top: 18px;
}

.hero-compact .hero-actions {
  margin-top: 28px;
}

/* 按钮下方的分类入口：一条细墨线压住，行内可换行 */
.hero-types {
  margin-top: 30px;
  padding-top: 16px;
  border-top: 1px solid var(--c-line);
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 10px;
}

.hero-types-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--c-ink-3);
}

.hero-types a {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 0.03em;
  color: var(--c-ink-2);
  border-bottom: 1px solid transparent;
  padding-bottom: 2px;
  transition:
    color var(--t-fast) ease-out,
    border-color var(--t-fast) ease-out;
}

.hero-types a:hover {
  color: var(--c-a2);
  border-bottom-color: var(--c-a2);
}

.hero-types b {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 400;
  color: var(--c-ink-3);
}

.home-compact :deep(.sec) {
  padding: 64px 0;
}

.home-compact :deep(.sec-head) {
  margin-bottom: 28px;
}

.home-compact :deep(.ink-stat-card) {
  padding: 26px 24px;
}
</style>
