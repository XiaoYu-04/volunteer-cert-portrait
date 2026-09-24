<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOrgOverview } from '@/api/volunteer'
import { getDashboard } from '@/api/analytics'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { formatPercent } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'
import InkChart from '@/components/charts/InkChart.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import OrgList from '@/components/biz/OrgList.vue'
import * as charts from '@/components/charts/options'

const toast = useToast()
const user = useUserStore()

const loading = ref(true)
const overview = ref(null)
const orgs = ref([])

/** 组织管理员登录后 info.orgId 即当前组织 */
const orgId = computed(() => user.info?.orgId || 1)

onMounted(async () => {
  try {
    const [detail, dashboard] = await Promise.all([getOrgOverview(orgId.value), getDashboard()])
    overview.value = detail
    orgs.value = dashboard?.orgs || []
  } catch (err) {
    toast.error(err.message || '数据加载失败')
  } finally {
    loading.value = false
  }
})

const stats = computed(() => overview.value?.stats || [])
const org = computed(() => overview.value?.org || null)

/**
 * 雷达图只画本组织。概览接口返回的组织字段是 name，
 * 而 charts.orgRadar 约定读 org，这里做一次映射。
 */
const radarOrg = computed(() =>
  org.value
    ? {
        org: org.value.name,
        activities: org.value.activities,
        signRate: org.value.signRate,
        passRate: org.value.passRate,
      }
    : null,
)

const orgBarOption = computed(() => (orgs.value.length ? charts.orgBar(orgs.value) : null))
const radarOption = computed(() => (radarOrg.value ? charts.orgRadar(radarOrg.value) : null))

/** 本组织在全校组织中的活跃度名次（按活动场次降序，未收录时为 0） */
const rank = computed(() => {
  if (!org.value) return 0
  const sorted = orgs.value.slice().sort((a, b) => b.activities - a.activities)
  return sorted.findIndex((item) => item.org === org.value.name) + 1
})
</script>

<template>
  <h1 class="console-title">组织数据</h1>
  <p class="console-sub">本组织活动的场次、报名、签到与时长认证情况。</p>

  <div class="stats stats-org" :aria-busy="loading">
    <InkStat
      v-for="item in stats"
      :key="item.key"
      :label="item.label"
      :value="item.value"
      :unit="item.unit"
      :delta="item.delta ?? null"
      :trend="item.trend"
    />
  </div>

  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">活跃度对比</span>
      <span class="panel-extra">{{ org?.name || '本组织' }}</span>
    </div>

    <div class="grid-2">
      <div>
        <InkChart
          :option="orgBarOption"
          :loading="loading"
          height="280px"
          label="全校各志愿服务组织活动场次横向条形图，按场次升序排列"
        />
        <p class="fig-cap">
          <b>图一</b>全校志愿服务组织活动场次（场），共 {{ orgs.length }} 个组织。
        </p>
      </div>

      <div>
        <InkChart
          :option="radarOption"
          :loading="loading"
          height="280px"
          label="本组织活动场次、签到率、审核通过率三项指标的雷达图"
        />
        <p class="fig-cap">
          <b>图二</b>{{ org?.name || '本组织' }}的场次、签到率与审核通过率。
        </p>
      </div>
    </div>
  </section>

  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">全校组织活跃度</span>
      <span class="panel-extra" v-if="rank">本组织排名第 {{ rank }} 位</span>
    </div>
    <OrgList :rows="orgs" />
  </section>

  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">组织档案</span>
    </div>

    <dl v-if="org" class="ink-desc">
      <div class="ink-desc-item">
        <dt class="ink-desc-k">组织名称</dt>
        <dd class="ink-desc-v">{{ org.name }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">组织编码</dt>
        <dd class="ink-desc-v num">{{ org.code }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">所属学院</dt>
        <dd class="ink-desc-v">{{ org.college }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">指导老师</dt>
        <dd class="ink-desc-v">{{ org.contact }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">联系电话</dt>
        <dd class="ink-desc-v num">{{ org.phone }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">成员人数</dt>
        <dd class="ink-desc-v num">{{ org.memberCount }} 人</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">成立时间</dt>
        <dd class="ink-desc-v num">{{ org.foundedAt }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">资质状态</dt>
        <dd class="ink-desc-v"><StatusTag type="org_status" :value="org.status" /></dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">累计活动</dt>
        <dd class="ink-desc-v num">{{ org.activities }} 场</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">签到率</dt>
        <dd class="ink-desc-v num">{{ formatPercent(org.signRate) }}</dd>
      </div>
      <div class="ink-desc-item">
        <dt class="ink-desc-k">审核通过率</dt>
        <dd class="ink-desc-v num">{{ formatPercent(org.passRate) }}</dd>
      </div>
      <div class="ink-desc-item span-2">
        <dt class="ink-desc-k">组织简介</dt>
        <dd class="ink-desc-v">{{ org.intro }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
/* 六列指标：复用 .stats 的细线分格，只改列数与边框断点 */
.stats-org {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

/* 指标区与首个面板之间没有现成的间距规则，这里补上 */
.stats + .panel {
  margin-top: 36px;
}

.stats-org :deep(.ink-stat-card) {
  padding: 24px 20px;
}

.stats-org :deep(.ink-stat-card:nth-child(3n)) {
  border-right: 1px solid var(--c-line);
}

.stats-org :deep(.ink-stat-card:nth-child(6n)) {
  border-right: 0;
}

@media (max-width: 1000px) {
  .stats-org {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .stats-org :deep(.ink-stat-card:nth-child(6n)) {
    border-right: 1px solid var(--c-line);
  }
  .stats-org :deep(.ink-stat-card:nth-child(3n)) {
    border-right: 0;
  }
}

@media (max-width: 620px) {
  .stats-org {
    grid-template-columns: 1fr;
  }
  .stats-org :deep(.ink-stat-card) {
    border-right: 0 !important;
  }
}
</style>
