<script setup>
import { formatPercent } from '@/utils/format'

/**
 * 组织活跃度列表。对应原型的 .ink-org-row：
 * 组织名占满剩余宽度，右侧三个等宽体指标 —— 场次 / 签到 / 通过。
 *
 * 传了 linkBase 时组织名变成可点链接：跳到「该组织的活动列表」。
 * 实现走**关键字搜索**而不是 orgId：活动列表的关键字同时匹配活动名与组织名
 * （后端 selectActivityPage 的谓词），所以只要把组织名填进关键字即可，
 * 落地页里那行关键字是可见可编辑的，用户能一眼看懂自己到了哪儿、也能顺手改掉。
 * 不带 linkBase 的页面（学校端看板、组织端看板）保持纯文本 —— 它们没有可跳的活动列表页。
 */
const props = defineProps({
  /** [{ org, activities, signRate, passRate }]，比率为 0~1 小数 */
  rows: { type: Array, default: () => [] },
  /** 活动列表页路径，如 '/student/activities'；留空则组织名不可点 */
  linkBase: { type: String, default: '' },
})

const toOrgActivities = (name) => `${props.linkBase}?keyword=${encodeURIComponent(name)}`

// 统一走 formatPercent：与看板、画像等处的百分比同为 1 位小数，
// 不再出现同一页「94 %」与「94.0%」两种写法。
const pct = (r) => formatPercent(r)
</script>

<template>
  <ul class="ink-org anim-stagger">
    <li v-for="row in rows" :key="row.org" class="ink-org-row">
      <RouterLink v-if="linkBase" class="ink-org-name ink-org-link" :to="toOrgActivities(row.org)">
        {{ row.org }}
      </RouterLink>
      <span v-else class="ink-org-name">{{ row.org }}</span>
      <span class="ink-org-metrics">
        <span class="ink-org-metric">
          <b class="ink-org-metric-num num">{{ row.activities }}</b>
          <span> 场</span>
        </span>
        <span class="ink-org-metric">
          <span>签到 </span>
          <b class="ink-org-metric-num num">{{ pct(row.signRate) }}</b>
        </span>
        <span class="ink-org-metric">
          <span>通过 </span>
          <b class="ink-org-metric-num num">{{ pct(row.passRate) }}</b>
        </span>
      </span>
    </li>
  </ul>
</template>

<style scoped>
/* 可点时的悬停反馈。基色/字体仍由 ink.css 的 .ink-org-name 给（继承自 base.css 的
   a{color:inherit; text-decoration:none}），这里只补「能点」的信号：悬停转朱砂色 +
   下划线。不常驻下划线 —— 一列组织名全带下划线会把排行读成链接列表。 */
.ink-org-link {
  transition: color var(--t-fast) ease-out;
}

.ink-org-link:hover {
  color: var(--c-a2);
  text-decoration: underline;
  text-underline-offset: 4px;
}
</style>
