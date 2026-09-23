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

/** 玫瑰图的无障碍描述：标签与人数都从接口数据拼，避免写死的数字随数据变化而失真 */
const distributionLabel = computed(() =>
  distribution.value.length
    ? `公益画像标签分布玫瑰图：${distribution.value.map((d) => `${d.tag} ${d.count} 人`).join('、')}`
    : '公益画像标签分布玫瑰图',
)

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

/**
 * 等级色调，星数越高色调越重。
 *
 * 键是接口返回的 `levelCode`（FIVE_STAR 等），**不要用等级中文名做键**：
 * 接口返回的是完整等级名（「五星志愿者」），用「五星」当键会全部落到 tone-mute 灰色，
 * 而 mock 里恰好是短名，于是这个 bug 只在接上真实接口后才看得见（待办 B20-3）。
 * 一星与普通志愿者沿用最轻的灰色 —— 低档位没有更淡的色调可用。
 */
const levelTone = {
  FIVE_STAR: 'ok',
  FOUR_STAR: 'info',
  THREE_STAR: 'warn',
  TWO_STAR: 'mute',
  ONE_STAR: 'mute',
  NORMAL: 'mute',
}

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
      <span class="panel-title">八类画像标签分布</span>
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
        :label="distributionLabel"
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
        <!-- 后端 tag 是「主标签」，无标签时为 null，被 non_null 策略整键省略 →
             这里必须兜底，否则渲染出一个空的朱砂边框方块 -->
        <span v-if="row.tag" class="tag-chip">{{ row.tag }}</span>
        <span v-else class="cell-mute">—</span>
      </template>

      <template #level="{ row }">
        <span class="ink-status" :class="`tone-${levelTone[row.levelCode] || 'mute'}`">
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