<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '@/api/analytics'
import { useToast } from '@/composables/useToast'
import { useDashboardText } from '@/composables/useDashboardText'
import { formatNumber } from '@/utils/format'

import InkSection from '@/components/common/InkSection.vue'
import InkStat from '@/components/common/InkStat.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkChart from '@/components/charts/InkChart.vue'
import ActivityCard from '@/components/biz/ActivityCard.vue'
import PortraitSeal from '@/components/biz/PortraitSeal.vue'
import RankList from '@/components/biz/RankList.vue'
import OrgList from '@/components/biz/OrgList.vue'
import NoticeList from '@/components/biz/NoticeList.vue'
import * as charts from '@/components/charts/options'

const toast = useToast()
const data = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    data.value = await getDashboard()
  } catch (err) {
    toast.error(err.message || '数据加载失败')
  } finally {
    loading.value = false
  }
})

/** 图表 option 只在数据就绪后计算，避免空数据下渲染出空图 */
const trendOption = computed(() => (data.value ? charts.trend(data.value.trend) : null))
const typePieOption = computed(() => (data.value ? charts.typePie(data.value.types) : null))
const collegeOption = computed(() => (data.value ? charts.college(data.value.colleges) : null))
const auditOption = computed(() => (data.value ? charts.audit(data.value.audit.items) : null))
const signOption = computed(() => (data.value ? charts.sign(data.value.signin) : null))

const topActivities = computed(() => (data.value?.activities || []).slice(0, 6))
const topColleges = computed(() => (data.value?.colleges || []).slice(0, 8))

/* 图注与无障碍描述一律从接口数据现算，见 composables/useDashboardText.js 的说明 */
const {
  signin,
  trendRange,
  trendTotal,
  trendDesc,
  trendLabel,
  typeLabel,
  typeCaption,
  collegeLabel,
  collegeDesc,
  auditLabel,
  signinLabel,
  portraitDesc,
} = useDashboardText(data)
</script>

<template>
  <!-- ==================== Hero ==================== -->
  <section class="hero">
    <div class="wrap hero-in">
      <div class="hero-main">
        <p class="hero-kicker">{{ trendRange || '志愿服务数据' }}</p>
        <h1>以微光聚炬，<br />让每一次<em>服务</em>都被记住</h1>
        <p class="hero-sub">
          活动发布、报名审核、签到签退、服务时长记录、多角色时长审核、学生公益画像——六个环节环环相扣，构成全校志愿服务的完整闭环。
        </p>
        <div class="hero-actions">
          <InkButton to="/student/activities" variant="primary">浏览志愿活动</InkButton>
          <InkButton to="/student/portrait">查看我的公益画像</InkButton>
        </div>
      </div>
      <div class="hero-side">
        <span class="vtitle" aria-hidden="true">志愿服务数据</span>
      </div>
    </div>
  </section>

  <div class="wrap">
    <!-- ==================== 核心指标 ==================== -->
    <InkSection>
      <div class="stats" :aria-busy="loading">
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
    <InkSection
      no="01 / 数据"
      title="全年志愿服务概览"
      :desc="trendDesc"
    >
      <div class="grid-2">
        <figure class="fig-frame" style="margin: 0">
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

        <figure class="fig-frame" style="margin: 0">
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
    <InkSection
      no="02 / 活动"
      title="本期志愿活动"
      desc="近期活动开放报名，可在线提交报名申请并等待组织管理员审核。"
    >
      <div class="grid-3">
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

    <!-- ==================== 公益画像 ==================== -->
    <InkSection
      no="03 / 画像"
      title="学生公益画像"
      :desc="portraitDesc"
    >
      <PortraitSeal :items="data?.profiles || []" />
    </InkSection>

    <!-- ==================== 流程 ==================== -->
    <InkSection
      no="04 / 流程"
      title="志愿服务流程"
      desc="四步闭环，多角色审核贯穿全程，服务时长经学校审核后计入公益画像。"
    >
      <ol class="ink-flow">
        <li v-for="(step, i) in data?.flow || []" :key="step.step" class="ink-flow-step">
          <span class="ink-flow-no num">{{ i + 1 }}</span>
          <span class="ink-flow-name">{{ step.step }}</span>
          <span class="ink-flow-desc">{{ step.desc }}</span>
        </li>
      </ol>
    </InkSection>

    <!-- ==================== 排行 + 组织 + 公告 ==================== -->
    <InkSection
      no="05 / 排行"
      title="学院志愿时长排行"
      :desc="collegeDesc"
    >
      <div class="grid-2">
        <div>
          <figure class="fig-frame" style="margin: 0 0 24px">
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
          <OrgList :rows="data?.orgs || []" />

          <h3 class="sub-head sub-head-gap">通知公告</h3>
          <NoticeList :rows="data?.notices || []" :limit="5" />
        </div>
      </div>
    </InkSection>

    <!-- ==================== 审核与签到 ==================== -->
    <InkSection
      no="06 / 审核"
      title="时长审核与签到"
      desc="组织提交时长后由学校管理员审核，通过率与签到率是衡量活动执行质量的两项核心指标。"
    >
      <div class="grid-2">
        <figure class="fig-frame" style="margin: 0">
          <InkChart
            :option="auditOption"
            :loading="loading"
            height="240px"
            :label="auditLabel"
          />
          <figcaption class="fig-cap"><b>图四</b>志愿服务时长审核三态分布（条）。</figcaption>
        </figure>

        <figure class="fig-frame" style="margin: 0">
          <InkChart
            :option="signOption"
            :loading="loading"
            height="240px"
            :label="signinLabel"
          />
          <figcaption class="fig-cap">
            <b>图五</b>活动签到率，应签到 {{ formatNumber(signin.total) }} 人次、实签到
            {{ formatNumber(signin.signed) }} 人次。
          </figcaption>
        </figure>
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

.sec-more {
  margin-top: 36px;
  display: flex;
  justify-content: center;
}
</style>