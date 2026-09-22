<script setup>
import { formatNumber } from '@/utils/format'

/**
 * 公益画像的印章式标签。对应原型的 .ink-tag：
 * 朱砂细边、白底、方形，标签名用衬线体。
 */
defineProps({
  /** [{ tag, count, desc }] */
  items: { type: Array, default: () => [] },
  showDesc: { type: Boolean, default: true },
})
</script>

<template>
  <ul class="tags">
    <li v-for="(item, i) in items" :key="item.tag" class="ink-tag" :class="`ink-tag-${i + 1}`">
      <span class="ink-tag-name">{{ item.tag }}</span>
      <span class="ink-tag-count">{{ formatNumber(item.count) }} 人</span>
      <span v-if="showDesc" class="ink-tag-desc">{{ item.desc }}</span>
    </li>
  </ul>
</template>