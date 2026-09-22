<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '@/api/analytics'
import { useToast } from '@/composables/useToast'

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
</script>

<template>
  <!-- ==================== Hero ==================== -->
  <section class="hero">
    <div class="wrap hero-in">
      <div class="hero-main">
        <p class="hero-kicker">2025 · 春季学期</p>
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
      desc="春季学期与秋季开学季是志愿服务高峰，3 月与 9 月单月活动量均超过 50 场。"
    >
      <div class="grid-2">
        <figure class="fig-frame" style="margin: 0">
          <InkChart
            :option="trendOption"
            :loading="loading"
            height="280px"
            label="2025 年逐月活动数量折线图，3 月 52 场、9 月 56 场为高峰，7 月 12 场最低"
          />
          <figcaption class="fig-cap"><b>图一</b>2025 年逐月活动数量（场），全年合计 386 场。</figcaption>
        </figure>

        <figure class="fig-frame" style="margin: 0">
          <InkChart
            :option="typePieOption"
            :loading="loading"
            height="280px"
            label="活动类型占比环形图：社区服务 96 场、环保行动 74 场、支教助学 62 场、大型赛会 58 场、校园服务 54 场、敬老助残 42 场"
          />
          <figcaption class="fig-cap"><b>图二</b>活动类型结构（场），社区服务占比最高。</figcaption>
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
      desc="系统依据参与活动的类型、频次与时长自动归类，全校 12,480 名学生已生成画像标签。"
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
      desc="按累计认证时长排序，计算机学院以 15,240 小时居首。"
    >
      <div class="grid-2">
        <div>
          <figure class="fig-frame" style="margin: 0 0 24px">
            <InkChart
              :option="collegeOption"
              :loading="loading"
              height="240px"
              label="各学院志愿时长排名横向条形图，计算机学院 15240 小时居首，体育学院 7980 小时最少"
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
            label="时长审核三态环形图：已通过 2142 条、待审核 186 条、已驳回 90 条，通过率 88.6%"
          />
          <figcaption class="fig-cap"><b>图四</b>志愿服务时长审核三态分布（条）。</figcaption>
        </figure>

        <figure class="fig-frame" style="margin: 0">
          <InkChart
            :option="signOption"
            :loading="loading"
            height="240px"
            label="签到率仪表盘：92.3%，应签到 2480 人次，实签到 2290 人次"
          />
          <figcaption class="fig-cap">
            <b>图五</b>活动签到率，应签到 2,480 人次、实签到 2,290 人次。
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