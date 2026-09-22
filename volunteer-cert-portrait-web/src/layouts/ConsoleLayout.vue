<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMenu } from '@/composables/useMenu'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

/** 组织端与学校端共用这套外壳，根路径从匹配到的父路由取 */
const rootPath = computed(() => route.matched[0]?.path || '/')
const groups = useMenu(() => rootPath.value)

/** 详情页高亮它所属的列表页 */
const isCurrent = (path) => {
  const active = route.meta.activeMenu || route.path
  return active === path
}

async function onLogout() {
  await user.logout()
  router.replace('/login')
}
</script>

<template>
  <a class="skip" href="#main">跳到主要内容</a>

  <div class="console">
    <aside class="console-side">
      <div class="console-brand">
        <span class="seal" aria-hidden="true">志愿</span>
        <span class="console-brand-text">
          <span class="console-brand-name">志愿服务数据志</span>
          <span class="console-brand-role">{{ user.roleLabel }}端</span>
        </span>
      </div>

      <nav class="console-nav" aria-label="功能菜单">
        <div v-for="group in groups" :key="group.name" class="console-nav-group">
          <span class="console-nav-group-title">{{ group.name }}</span>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :class="{ 'is-current': isCurrent(item.path) }"
            :aria-current="isCurrent(item.path) ? 'page' : undefined"
          >
            {{ item.title }}
          </RouterLink>
        </div>
      </nav>

      <div class="console-user">
        <span class="console-user-name">{{ user.name }}</span>
        <span class="console-user-meta">{{ user.roleLabel }}</span>
        <button class="console-user-out" type="button" @click="onLogout">退出登录</button>
      </div>
    </aside>

    <div class="console-main">
      <div id="main" class="console-body">
        <RouterView />
      </div>
    </div>
  </div>
</template>