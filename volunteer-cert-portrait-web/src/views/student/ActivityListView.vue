<script setup>
import { onMounted, ref } from 'vue'
import { listActivities, listCategories } from '@/api/volunteer'
import { useTable } from '@/composables/useTable'
import { useDictStore } from '@/stores/dict'

import InkField from '@/components/common/InkField.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkEmpty from '@/components/common/InkEmpty.vue'
import ActivityGridCard from '@/components/biz/ActivityGridCard.vue'

const dict = useDictStore()

// pageSize 取 12 而不是 useTable 的默认 10：本页 .act-grid 是**固定 3 列**，
// 10 条会排成 3+3+3+1，最后一行只落 1 张卡、空出 2 格（曾被报为「底部少了两个」）。
// 12 = 3×4，整四行铺满，不留空格。
// 配套前提：InkPagination 的每页条数下拉必须含 12，否则下拉选不中任何一项、显示与实际不一致。
const { rows, total, loading, query, search } = useTable(listActivities, {
  defaultQuery: { pageSize: 12, keyword: '', type: '', status: 'PUBLISHED' },
})

function resetQuery() {
  query.keyword = ''
  query.type = ''
  query.status = 'PUBLISHED'
  search()
}

const categories = ref([])
onMounted(async () => {
  dict.load()
  categories.value = await listCategories()
})

const statusOptions = dict.options('activity_status')
</script>

<template>
  <div class="wrap">
    <header class="page-head">
      <span class="hero-kicker">志愿活动</span>
      <h1>浏览志愿活动</h1>
      <p class="page-head-sub">按类型与关键字筛选，查看详情与剩余名额。</p>
    </header>

    <section class="sec-list" :aria-busy="loading">
      <form class="ink-filter" @submit.prevent="search">
        <InkField label="关键字">
          <input
            v-model.trim="query.keyword"
            class="ink-input"
            type="search"
            placeholder="活动名称"
          />
        </InkField>

        <InkField label="活动类型">
          <select v-model="query.type" class="ink-select">
            <option value="">全部类型</option>
            <option v-for="c in categories" :key="c.id" :value="c.name">{{ c.name }}</option>
          </select>
        </InkField>

        <InkField label="状态">
          <select v-model="query.status" class="ink-select">
            <option value="">全部状态</option>
            <option v-for="s in statusOptions" :key="s.value" :value="s.value">
              {{ s.label }}
            </option>
          </select>
        </InkField>

        <div class="ink-filter-actions">
          <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
          <InkButton size="sm" @click="resetQuery">重置</InkButton>
        </div>
      </form>

      <div v-if="loading" class="act-grid sk-grid" aria-hidden="true">
        <!-- 骨架数量与每页条数一致（12），否则加载完从 2 行跳到 4 行会抖一下 -->
        <div v-for="n in 12" :key="`sk-${n}`" class="ink-agc">
          <span class="ink-skeleton ink-agc-sk-cover"></span>
          <div class="ink-agc-body sk-body">
            <span class="ink-skeleton ink-skeleton-row"></span>
            <span class="ink-skeleton ink-skeleton-row"></span>
          </div>
        </div>
      </div>

      <div v-else-if="rows.length" class="act-grid">
        <ActivityGridCard
          v-for="row in rows"
          :key="row.id"
          :activity="row"
          :to="`/student/activities/${row.id}`"
        />
      </div>

      <InkEmpty
        v-else
        text="没有符合条件的活动"
        hint="试试换个关键字，或把类型与状态放宽到「全部」。"
      />

      <InkPagination
        v-model:page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :sizes="[12, 24, 48]"
      />
    </section>
  </div>
</template>

<style scoped>

/* 骨架卡的正文区比真实卡片矮，补一点下内边距免得贴边 */
.sk-body {
  padding-bottom: 20px;
}

/* 占位卡不是可点元素：别让它们继承真卡片「悬停变墨色边框」的反馈 */
.sk-grid .ink-agc {
  pointer-events: none;
}
</style>
