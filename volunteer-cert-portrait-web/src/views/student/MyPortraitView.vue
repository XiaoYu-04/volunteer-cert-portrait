<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMyPortrait } from '@/api/portrait'
import { useToast } from '@/composables/useToast'
import { formatDateTime, formatPercent } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'
import InkEmpty from '@/components/common/InkEmpty.vue'
import InkProgress from '@/components/common/InkProgress.vue'
import InkChart from '@/components/charts/InkChart.vue'
import * as charts from '@/components/charts/options'

const toast = useToast()

const portrait = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    portrait.value = await getMyPortrait()
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
})

/** profileBar 工厂接收 [{ tag, count }]，画像维度给的是 [{ name, value }] */
const dimOption = computed(() =>
  portrait.value
    ? charts.profileBar(
        portrait.value.dimensions.map((d) => ({ tag: d.name, count: d.value })),
        { yName: '分' },
      )
    : null,
)

const dimLabel = computed(() =>
  (portrait.value?.dimensions || []).map((d) => `${d.name} ${d.value} 分`).join('，'),
)
</script>

<template>
  <div class="wrap">
    <header class="page-head">
      <span class="hero-kicker">公益画像</span>
      <h1>我的公益画像</h1>
      <p class="page-head-sub">按活动类型、频次与时长自动归类，各项维度满分 100 分。</p>
    </header>

    <section class="sec-list">
      <div v-if="loading" class="panel" aria-busy="true">
        <span class="ink-skeleton ink-skeleton-row"></span>
        <span class="ink-skeleton ink-skeleton-row"></span>
        <span class="ink-skeleton ink-skeleton-row"></span>
      </div>

      <InkEmpty
        v-else-if="!portrait"
        text="画像尚未生成"
        hint="参与活动并完成服务时长审核后自动生成"
      />

      <template v-else>
        <div class="stats">
          <InkStat label="累计服务时长" :value="portrait.totalHours" unit="小时" />
          <InkStat label="参与活动" :value="portrait.activityCount" unit="场" />
          <InkStat label="画像等级" :value="portrait.level" />
        </div>

        <div class="grid-2 grid-gap">
          <div class="panel">
            <div class="panel-head">
              <h2 class="panel-title">画像标签</h2>
              <span class="panel-extra panel-time num">{{ formatDateTime(portrait.generatedAt) }}</span>
            </div>

            <div class="ink-tag portrait-tag">
              <!-- 后端 tag 是「主标签」（印章一枚只放得下一个），无标签时为 null，
                   被 non_null 策略整键省略 —— 必须兜底，否则印章里是空的 -->
              <span class="ink-tag-name">{{ portrait.tag || '暂无标签' }}</span>
              <span class="seal-level">{{ portrait.level }}</span>
              <span class="ink-tag-desc">按活动类型与参与频次自动归类</span>
            </div>

            <dl class="ink-desc desc-one">
              <div class="ink-desc-item">
                <dt class="ink-desc-k">姓名</dt>
                <dd class="ink-desc-v">{{ portrait.studentName }}</dd>
              </div>
              <div class="ink-desc-item">
                <dt class="ink-desc-k">学号</dt>
                <dd class="ink-desc-v num">{{ portrait.studentNo }}</dd>
              </div>
              <div class="ink-desc-item">
                <dt class="ink-desc-k">学院</dt>
                <dd class="ink-desc-v">{{ portrait.college }}</dd>
              </div>
              <div class="ink-desc-item">
                <dt class="ink-desc-k">专业年级</dt>
                <dd class="ink-desc-v">{{ portrait.major }} · {{ portrait.grade }} 级</dd>
              </div>
              <div class="ink-desc-item">
                <dt class="ink-desc-k">社区服务占比</dt>
                <dd class="ink-desc-v num">{{ formatPercent(portrait.communityRatio) }}</dd>
              </div>
              <div class="ink-desc-item">
                <dt class="ink-desc-k">环保行动占比</dt>
                <dd class="ink-desc-v num">{{ formatPercent(portrait.environmentRatio) }}</dd>
              </div>
            </dl>
          </div>

          <div class="panel">
            <div class="panel-head">
              <h2 class="panel-title">画像维度</h2>
              <span class="panel-extra">满分 100</span>
            </div>

            <ul class="dim-list">
              <li v-for="dim in portrait.dimensions" :key="dim.name" class="dim-item">
                <span class="dim-name">{{ dim.name }}</span>
                <InkProgress :value="dim.value" :max="100" show-text />
              </li>
            </ul>
          </div>
        </div>

        <figure class="fig-frame fig-gap">
          <InkChart
            :option="dimOption"
            :loading="loading"
            height="300px"
            :label="`公益画像维度得分柱状图：${dimLabel}`"
          />
          <figcaption class="fig-cap">
            <b>图一</b>画像各维度得分（满分 100）。
          </figcaption>
        </figure>
      </template>
    </section>
  </div>
</template>

<style scoped>

.grid-gap {
  margin-top: var(--sp-8);
}

/* 生成时间是元信息，别和面板标题抢注意力 */
.panel-time {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-3);
}

.portrait-tag {
  max-width: 260px;
}

.seal-level {
  display: block;
  margin-top: 12px;
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: 0.1em;
  color: var(--c-ink);
}

.desc-one {
  grid-template-columns: 1fr;
  margin-top: 30px;
}

.dim-list {
  padding-top: 4px;
}

.dim-item {
  display: grid;
  grid-template-columns: 6.5em minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--c-line-2);
}

.dim-item:last-child {
  border-bottom: 0;
}

.dim-name {
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.06em;
  color: var(--c-ink-2);
}

.fig-gap {
  margin: var(--sp-8) 0 0;
}
</style>
