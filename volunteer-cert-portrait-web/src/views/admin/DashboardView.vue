<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '@/api/analytics'
import { useToast } from '@/composables/useToast'
import { useDashboardText } from '@/composables/useDashboardText'
import { formatNumber, formatPercent } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'
import InkChart from '@/components/charts/InkChart.vue'
import RankList from '@/components/biz/RankList.vue'
import OrgList from '@/components/biz/OrgList.vue'
import * as charts from '@/components/charts/options'

const toast = useToast()

const data = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    data.value = await getDashboard()
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
})

/** 图表 option 只在数据就绪后计算，避免空数据下渲染出空图 */
const trendOption = computed(() => (data.value ? charts.trend(data.value.trend) : null))
const hoursOption = computed(() => (data.value ? charts.hours(data.value.trend) : null))
const typePieOption = computed(() => (data.value ? charts.typePie(data.value.types) : null))
const collegeOption = computed(() => (data.value ? charts.college(data.value.colleges) : null))
const signOption = computed(() => (data.value ? charts.sign(data.value.signin) : null))
const auditOption = computed(() => (data.value ? charts.audit(data.value.audit.items) : null))
const heatOption = computed(() => (data.value ? charts.heat(data.value.heatmap) : null))

const colleges = computed(() => data.value?.colleges || [])
const orgs = computed(() => data.value?.orgs || [])
const heatMonth = computed(() => {
  const month = data.value?.heatmap?.month
  return month ? `${month.slice(0, 4)} 年 ${Number(month.slice(5))} 月` : ''
})

/* 图注与无障碍描述一律从接口数据现算，见 composables/useDashboardText.js 的说明 */
const {
  trendTotal,
  trendHours,
  trendDesc,
  trendLabel,
  hoursLabel,
  typeLabel,
  typeCaption,
  collegeLabel,
  auditLabel,
  auditDesc,
  signinLabel,
  structureNote,
  heatLabel,
} = useDashboardText(data)
</script>

<template>
  <h1 class="console-title">全校数据看板</h1>
  <p class="console-sub">
    汇总全校志愿服务的关键指标：活动规模、服务时长、类型结构与学院分布、时长审核与签到质量。
    数据由各组织日常业务沉淀而成，供认证决策与学期总结使用。
  </p>

  <!-- 核心指标 -->
  <div class="stats stats-console" :aria-busy="loading">
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

  <!-- 趋势 -->
  <section class="panel panel-gap">
    <div class="panel-head">
      <span class="panel-title">活动与服务时长趋势</span>
      <span class="panel-extra panel-note">
        近 12 个月 {{ formatNumber(trendTotal) }} 场 · {{ formatNumber(trendHours) }} 小时
      </span>
    </div>

    <div class="grid-2">
      <figure class="dash-fig">
        <InkChart
          :option="trendOption"
          :loading="loading"
          height="260px"
          :label="trendLabel"
        />
        <figcaption class="fig-cap">
          <b>图一</b>逐月活动数量（场），近 12 个月合计 {{ formatNumber(trendTotal) }} 场。{{ trendDesc }}
        </figcaption>
      </figure>

      <figure class="dash-fig">
        <InkChart
          :option="hoursOption"
          :loading="loading"
          height="260px"
          :label="hoursLabel"
        />
        <figcaption class="fig-cap">
          <b>图二</b>逐月服务时长（小时），近 12 个月合计 {{ formatNumber(trendHours) }} 小时，与活动量走势一致。
        </figcaption>
      </figure>
    </div>
  </section>

  <!-- 结构与分布 -->
  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">活动结构与学院分布</span>
      <span class="panel-extra panel-note">{{ structureNote }}</span>
    </div>

    <div class="grid-2">
      <figure class="dash-fig">
        <InkChart
          :option="typePieOption"
          :loading="loading"
          height="280px"
          :label="typeLabel"
        />
        <figcaption class="fig-cap">
          <b>图三</b>活动类型结构（场），{{ typeCaption }}。
        </figcaption>
      </figure>

      <figure class="dash-fig">
        <InkChart
          :option="collegeOption"
          :loading="loading"
          height="280px"
          :label="collegeLabel"
        />
        <figcaption class="fig-cap">
          <b>图四</b>各学院累计认证志愿时长（小时）。
        </figcaption>
      </figure>
    </div>
  </section>

  <!-- 审核与签到 -->
  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">时长审核与签到质量</span>
      <span class="panel-extra panel-note">通过率 {{ formatPercent(data?.audit?.passRate) }}</span>
    </div>

    <div class="grid-2">
      <figure class="dash-fig">
        <InkChart
          :option="auditOption"
          :loading="loading"
          height="240px"
          :label="auditLabel"
        />
        <figcaption class="fig-cap">
          <b>图五</b>{{ auditDesc }}
        </figcaption>
      </figure>

      <figure class="dash-fig">
        <InkChart
          :option="signOption"
          :loading="loading"
          height="240px"
          :label="signinLabel"
        />
        <figcaption class="fig-cap">
          <b>图六</b>活动签到率，是衡量活动执行质量的核心指标。
        </figcaption>
      </figure>
    </div>

    <dl class="quality-metrics">
      <div class="quality-metric">
        <dt>应签到人次</dt>
        <dd class="num">{{ formatNumber(data?.signin?.total || 0) }}</dd>
      </div>
      <div class="quality-metric">
        <dt>实签到人次</dt>
        <dd class="num">{{ formatNumber(data?.signin?.signed || 0) }}</dd>
      </div>
      <div class="quality-metric">
        <dt>缺勤人次</dt>
        <dd class="num">{{ formatNumber(data?.signin?.absent || 0) }}</dd>
      </div>
      <div class="quality-metric">
        <dt>审核待处理</dt>
        <dd class="num">{{ formatNumber(data?.audit?.items?.[1]?.value || 0) }}</dd>
      </div>
    </dl>
  </section>

  <!-- 签到热力 -->
  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">签到热力日历</span>
      <span class="panel-extra panel-note">{{ heatMonth }}</span>
    </div>

    <figure class="dash-fig">
      <InkChart
        :option="heatOption"
        :loading="loading"
        height="240px"
        :label="heatLabel"
      />
      <figcaption class="fig-cap">
        <b>图七</b>逐日签到人次，颜色越深代表当日参与服务的人次越多。
      </figcaption>
    </figure>
  </section>

  <!-- 排行与组织 -->
  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">学院排行与组织活跃度</span>
    </div>

    <div class="grid-2">
      <div>
        <h3 class="dash-sub">学院志愿时长排行</h3>
        <RankList :rows="colleges" />
      </div>

      <div>
        <h3 class="dash-sub">组织活跃度</h3>
        <OrgList :rows="orgs" />
      </div>
    </div>
  </section>

  <!-- 认证流程 -->
  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">服务时长认证流程</span>
    </div>

    <ol class="ink-flow">
      <li v-for="(step, i) in data?.flow || []" :key="step.step" class="ink-flow-step">
        <span class="ink-flow-no num">{{ i + 1 }}</span>
        <span class="ink-flow-name">{{ step.step }}</span>
        <span class="ink-flow-desc">{{ step.desc }}</span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.stats-console {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.stats-console :deep(.ink-stat-card) {
  padding: 24px 20px;
}

.stats-console :deep(.ink-stat-card:nth-child(3n)) {
  border-right: 1px solid var(--c-line);
}

.stats-console :deep(.ink-stat-card:nth-child(6n)) {
  border-right: 0;
}

.panel-gap {
  margin-top: 36px;
}

.panel-note {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-3);
}

/* 面板内部已经有边框，图框不再重复描边 */
.dash-fig {
  margin: 0;
}

.dash-sub {
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: 0.06em;
  color: var(--c-ink-2);
  margin-bottom: 10px;
}

.quality-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 28px;
  border-top: 1px solid var(--c-ink);
}

.quality-metric {
  padding: 18px 0 4px;
  border-bottom: 1px solid var(--c-line-2);
  border-right: 1px solid var(--c-line-2);
  padding-left: 20px;
}

.quality-metric:first-child {
  padding-left: 0;
}

.quality-metric:last-child {
  border-right: 0;
}

.quality-metric dt {
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 0.08em;
  color: var(--c-ink-3);
}

.quality-metric dd {
  margin-top: 10px;
  font-size: 22px;
  color: var(--c-ink);
}

@media (max-width: 1000px) {
  .stats-console {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stats-console :deep(.ink-stat-card:nth-child(6n)) {
    border-right: 1px solid var(--c-line);
  }

  .stats-console :deep(.ink-stat-card:nth-child(3n)) {
    border-right: 0;
  }

  .quality-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quality-metric:nth-child(2n) {
    border-right: 0;
  }
}

@media (max-width: 620px) {
  .stats-console {
    grid-template-columns: 1fr;
  }

  .stats-console :deep(.ink-stat-card) {
    border-right: 0 !important;
  }

  .quality-metrics {
    grid-template-columns: 1fr;
  }

  .quality-metric {
    padding-left: 0;
    border-right: 0;
  }
}
</style>