<script setup>
import { useUserStore } from '@/stores/user'
import InkButton from '@/components/common/InkButton.vue'

const user = useUserStore()
</script>

<template>
  <div class="err">
    <div class="err-inner">
      <span class="err-seal" aria-hidden="true">禁</span>
      <p class="err-code num">403</p>
      <h1 class="err-title">无权访问该页面</h1>
      <p class="err-desc">
        当前登录角色为「{{ user.roleLabel || '未登录' }}」。<br />
        如需访问该页面，请联系学校管理员调整角色。
      </p>
      <div class="err-actions">
        <InkButton :to="user.isLogin ? user.homePath : '/login'" variant="primary">
          {{ user.isLogin ? '返回我的首页' : '去登录' }}
        </InkButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.err {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 40px 24px;
}

.err-inner {
  max-width: 460px;
  text-align: center;
}

.err-seal {
  display: inline-grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 28px;
  background: var(--c-a2);
  color: #fff;
  font-family: var(--font-display);
  font-size: 22px;
}

.err-code {
  font-family: var(--font-display);
  font-size: clamp(56px, 10vw, 96px);
  line-height: 1;
  letter-spacing: 0.06em;
  color: var(--c-ink);
}

.err-title {
  margin-top: 20px;
  font-family: var(--font-display);
  font-size: 22px;
  letter-spacing: 0.08em;
  color: var(--c-ink);
}

.err-desc {
  margin-top: 16px;
  font-size: 14px;
  line-height: 2;
  color: var(--c-ink-3);
}

.err-actions {
  margin-top: 32px;
  display: flex;
  justify-content: center;
}
</style>
