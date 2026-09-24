<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMenu } from '@/composables/useMenu'

import ChangePasswordDialog from '@/components/biz/ChangePasswordDialog.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

// 组织端 / 学校端没有个人中心页，侧栏是这两类账号唯一的自助改密入口
const pwdOpen = ref(false)

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
        <div class="console-user-actions">
          <button class="console-user-out" type="button" @click="pwdOpen = true">修改密码</button>
          <button class="console-user-out" type="button" @click="onLogout">退出登录</button>
        </div>
      </div>
    </aside>

    <div class="console-main">
      <div id="main" class="console-body">
        <RouterView />
      </div>
    </div>
  </div>

  <ChangePasswordDialog v-model="pwdOpen" />
</template>

<style scoped>
/* 两个文字按钮复用全局的 .console-user-out，这里只负责并排与间距 */
.console-user-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}

.console-user-actions .console-user-out {
  margin-top: 0;
}
</style>
