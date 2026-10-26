<script setup>
import { RouterLink } from 'vue-router'

/**
 * 首页「志愿风采」照片墙。
 *
 * 与 Hero 轮播的区别：这里是静态的错落图集，标题/组织写在图片下方，
 * 用细墨线压边，更像展览墙面；轮播只取前 6 张，这里最多展示 10 张
 * （真后端有 10 场演示活动封面，mock 有 6 张）。
 */
defineProps({
  items: { type: Array, default: () => [] },
  /** 详情页前缀，最终地址为 `${toBase}/${item.id}` */
  toBase: { type: String, default: '/student/activities' },
})

/** 封面缺失 / 加载失败时收起 img，露出底色与标题，不留破图标 */
function onImageError(event) {
  event.currentTarget.hidden = true
}
</script>

<template>
  <div v-if="items.length" class="ink-gallery">
    <RouterLink
      v-for="(item, index) in items"
      :key="item.id"
      class="ink-gallery-item"
      :to="`${toBase}/${item.id}`"
    >
      <figure class="ink-gallery-fig">
        <img
          class="ink-gallery-img"
          :src="item.cover"
          :alt="item.title"
          loading="lazy"
          decoding="async"
          @error="onImageError"
        />
        <figcaption class="ink-gallery-cap">
          <span class="ink-gallery-no">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="ink-gallery-title">{{ item.title }}</span>
          <span class="ink-gallery-meta">
            {{ [item.type, item.org].filter(Boolean).join(' · ') }}
          </span>
        </figcaption>
      </figure>
    </RouterLink>
  </div>
</template>
