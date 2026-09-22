<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDistribution, listPortraits } from '@/api/portrait'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { formatNumber, formatPercent } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'
import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkChart from '@/components/charts/InkChart.vue'
import PortraitSeal from '@/components/biz/PortraitSeal.vue'
import * as charts from '@/components/charts/options'

const toast = useToast()

const distribution = ref([])

const { rows, total, loading, query, search } = useTable(listPortraits, {
  defaultQuery: { keyword: '', college: '', tag: '' },
})

onMounted(async () => {
  try {
    distribution.value = await getDistribution()
  } catch (err) {
    toast.error(err.message)
  }
})

const roseOption = computed(() =>
  distribution.value.length ? charts.profile(distribution.value) : null,
)

const portraitTotal = computed(() => distribution.value.reduce((sum, item) => sum + item.count, 0))
const topTag = computed(() => distribution.value[0] || null)

const columns = [
  { key: 'studentName', title: '学生', width: '110px' },
  { key: 'studentNo', title: '学号', width: '120px' },
  { key: 'college', title: '学院' },
  { key: 'tag', title: '画像标签', width: '120px' },
  { key: 'level', title: '等级', width: '90px' },
  { key: 'totalHours', title: '累计时长', width: '100px', align: 'right' },
  { key: 'activityCount', title: '活动场次', width: '100px', align: 'right' },
  { key: 'communityRatio', title: '社区类占比', width: '110px', align: 'right' },
  { key: 'environmentRatio', title: '环保类占比', width: '110px', align: 'right' },
]

/** 等级色调，星数越高色调越重 */
const levelTone = { 五星: 'ok', 四星: 'info', 三星: 'warn', 二星: 'mute' }

function resetQuery() {
  query.keyword = ''
  query.college = ''
  query.tag = ''
  search()
}
</script>

<template>
  <h1 class="console-title">学生公益画像</h1>
  <p class="console-sub">
    画像由系统依据学生参与活动的类型、频次与累计时长自动归类，每学期更新一次。
    可按学院与标签筛选查看明细，用于评选推荐与帮扶对象识别。
  </p>

  <div class="stats">
    <InkStat label="已生成画像" :value="portraitTotal" unit="人" :delta="null" />
    <InkStat label="画像标签" :value="distribution.length" unit="类" :delta="null" />
    <InkStat
      :label="`最大标签 · ${topTag?.tag || '—'}`"
      :value="topTag?.count || 0"
      unit="人"
      :delta="null"
    />
  </div>

  <section class="panel panel-gap">
    <div class="panel-head">
      <span class="panel-title">六类画像标签分布</span>
      <span class="panel-extra panel-note">画像合计 {{ formatNumber(portraitTotal) }} 人</span>
    </div>

    <PortraitSeal :items="distribution" />
  </section>

  <section class="panel">
    <div class="panel-head">
      <span class="panel-title">标签占比</span>
    </div>

    <figure class="dash-fig">
      <InkChart
        :option="roseOption"
        :loading="!distribution.length"
        height="340px"
        label="公益画像标签分布玫瑰图：热心志愿者 3180 人、长期坚持型 2460 人、社区服务型 2240 人、环保行动型 1860 人、大型活动型 1540 人、校园服务型 1200 人"
      />
      <figcaption class="fig-cap">
        <b>图一</b>各标签学生人数（人），玫瑰半径与该标签人数成正比。
      </figcaption>
    </figure>
  </section>

  <section class="panel">
    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input
          v-model.trim="query.keyword"
          class="ink-input"
          type="search"
          placeholder="学生姓名或学号"
        />
      </InkField>

      <InkField label="学院">
        <input v-model.trim="query.college" class="ink-input" type="text" placeholder="如 计算机学院" />
      </InkField>

      <InkField label="画像标签">
        <select v-model="query.tag" class="ink-select">
          <option value="">全部标签</option>
          <option v-for="item in distribution" :key="item.tag" :value="item.tag">
            {{ item.tag }}
          </option>
        </select>
      </InkField>

      <div class="ink-filter-actions">
        <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
        <InkButton size="sm" @click="resetQuery">重置</InkButton>
      </div>
    </form>

    <div class="panel-head">
      <span class="panel-title">画像明细</span>
    </div>

    <InkTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      row-key="studentId"
      empty-text="没有符合条件的画像记录"
    >
      <template #studentName="{ row }">
        <span class="cell-strong">{{ row.studentName }}</span>
      </template>

      <template #studentNo="{ row }">
        <span class="col-num">{{ row.studentNo }}</span>
      </template>

      <template #tag="{ row }">
        <span class="tag-chip">{{ row.tag }}</span>
      </template>

      <template #level="{ row }">
        <span class="ink-status" :class="`tone-${levelTone[row.level] || 'mute'}`">
          {{ row.level }}
        </span>
      </template>

      <template #totalHours="{ row }">
        <span class="col-num">{{ row.totalHours }}</span>
      </template>

      <template #activityCount="{ row }">
        <span class="col-num">{{ row.activityCount }}</span>
      </template>

      <template #communityRatio="{ row }">
        <span class="col-num">{{ formatPercent(row.communityRatio) }}</span>
      </template>

      <template #environmentRatio="{ row }">
        <span class="col-num">{{ formatPercent(row.environmentRatio) }}</span>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
  </section>
</template>

<style scoped>
.panel-gap {
  margin-top: 36px;
}

.panel-note {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-3);
}

.dash-fig {
  margin: 0;
}

.cell-strong {
  font-family: var(--font-display);
  color: var(--c-ink);
}

/* 印章式标签，沿用画像标签的朱砂细边语言 */
.tag-chip {
  display: inline-block;
  padding: 2px 8px;
  font-family: var(--font-display);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-a2);
  border: 1px solid var(--c-a2);
  background: #fff;
  white-space: nowrap;
}

.ink-filter-actions {
  display: flex;
  gap: 12px;
  padding-bottom: 2px;
}
</style>