<script setup>
/**
 * 通用区块：88px 纵向留白 + 细墨线分隔 + 三栏区块头（编号 / 标题 / 说明）。
 * 三个格子始终渲染，否则栅格的 2-6-4 分栏会错位。
 */
defineProps({
  /** 区块编号，如 '01 / 数据' */
  no: { type: String, default: '' },
  title: { type: String, default: '' },
  desc: { type: String, default: '' },
})
</script>

<template>
  <section class="sec">
    <div class="sec-head">
      <span class="no">{{ no }}</span>
      <h2>{{ title }}</h2>
      <p>{{ desc }}</p>
    </div>
    <slot />
  </section>
</template>