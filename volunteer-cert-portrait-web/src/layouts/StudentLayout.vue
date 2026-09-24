<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMenu } from '@/composables/useMenu'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const groups = useMenu('/student')

/** 顶部导航把分组拍平成一行，学生端条目少，不需要二级菜单 */
const navItems = computed(() => groups.value.flatMap((g) => g.items))

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
  <div class="student-shell">
    <a class="skip" href="#main">跳到主要内容</a>

    <header class="top">
      <div class="wrap top-in">
        <RouterLink class="brand" :to="user.homePath">
          <span class="seal" aria-hidden="true">志愿</span>
          <span>
            <span class="brand-name">志愿服务数据志</span>
            <span class="brand-sub">VOLUNTEER SERVICE</span>
          </span>
        </RouterLink>

        <nav class="top-nav" aria-label="主导航">
          <RouterLink
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            :class="{ 'is-current': isCurrent(item.path) }"
            :aria-current="isCurrent(item.path) ? 'page' : undefined"
          >
            {{ item.title }}
          </RouterLink>

          <span class="top-user">
            <span class="top-user-name">{{ user.name }}</span>
            <button class="top-user-out" type="button" @click="onLogout">退出</button>
          </span>
        </nav>
      </div>
    </header>

    <main id="main">
      <RouterView />
    </main>

    <footer class="foot">
      <div class="wrap foot-in">
        <RouterLink class="foot-brand" :to="user.homePath">
          <span class="seal" aria-hidden="true">志愿</span>
          <span class="foot-brand-name">志愿服务数据志</span>
        </RouterLink>

        <nav class="foot-nav" aria-label="页脚导航">
          <RouterLink v-for="item in navItems" :key="item.path" :to="item.path">
            {{ item.title }}
          </RouterLink>
        </nav>

        <p class="foot-copy">© 2026 · 高校志愿服务时长认证与公益画像数据分析系统</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.student-shell {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

.student-shell > main {
  flex: 1 0 auto;
}

.top-user {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding-left: 22px;
  border-left: 1px solid var(--c-line);
}

.top-user-name {
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.06em;
  color: var(--c-ink);
}

.top-user-out {
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--c-ink-3);
  border-bottom: 1px solid var(--c-line);
  transition: color 0.15s ease-out, border-color 0.15s ease-out;
}

.top-user-out:hover {
  color: var(--c-a2);
  border-bottom-color: var(--c-a2);
}

@media (max-width: 1000px) {
  .top-in {
    flex-wrap: wrap;
  }
  .top-nav {
    width: 100%;
    margin-left: 0;
    flex-wrap: wrap;
    gap: 16px;
    padding-bottom: 6px;
  }
  .top-user {
    border-left: 0;
    padding-left: 0;
  }
}
</style>
