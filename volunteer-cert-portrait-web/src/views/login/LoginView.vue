<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import InkField from '@/components/common/InkField.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const toast = useToast()

const form = reactive({ username: '', password: '' })
const errors = reactive({ username: '', password: '' })
const loading = ref(false)

/**
 * 演示账号，一键填入。
 *
 * 这里刻意**不写学号**：mock 与真后端是两套互不相干的演示数据
 * （真库 student → 张同学 / 20230001，mock → 陈思远 / 202210001），
 * 把其中一个学号写进这份共用代码，切到另一套就成了一句话假话。
 * 学号可登录这件事由输入框的 hint 说明，不靠演示区列值。
 */
const demos = [
  { role: '学生', username: 'student', password: '123456' },
  { role: '组织管理员', username: 'org_admin', password: '123456' },
  { role: '学校管理员', username: 'admin', password: '123456' },
]

function fill(demo) {
  form.username = demo.username
  form.password = demo.password
  errors.username = ''
  errors.password = ''
}

function validate() {
  errors.username = form.username.trim() ? '' : '请输入用户名或学号'
  errors.password = form.password ? '' : '请输入密码'
  return !errors.username && !errors.password
}

async function onSubmit() {
  if (!validate() || loading.value) return
  loading.value = true
  try {
    const info = await user.login({ ...form })
    toast.success(`欢迎回来，${info.name}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect || user.homePath)
  } catch (err) {
    toast.error(err.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <aside class="auth-aside">
      <div class="auth-aside-brand">
        <span class="seal" aria-hidden="true">志愿</span>
        <span class="auth-aside-name">志愿服务数据志</span>
      </div>

      <p class="auth-aside-quote">全校志愿服务<br /><em>时长认证</em>与公益画像</p>

      <p class="auth-aside-foot">VOLUNTEER SERVICE · VCP</p>
    </aside>

    <main class="auth-main">
      <div class="auth-card">
        <h1 class="auth-title">登录</h1>
        <p class="auth-sub">高校志愿服务时长认证与公益画像数据分析系统</p>

        <form novalidate @submit.prevent="onSubmit">
          <InkField label="账号" required :error="errors.username" hint="支持用户名或学号登录">
            <input
              v-model.trim="form.username"
              class="ink-input"
              type="text"
              autocomplete="username"
              placeholder="用户名或学号"
              @blur="errors.username = form.username ? '' : '请输入用户名或学号'"
            />
          </InkField>

          <InkField label="密码" required :error="errors.password">
            <input
              v-model="form.password"
              class="ink-input"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              @blur="errors.password = form.password ? '' : '请输入密码'"
            />
          </InkField>

          <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
        </form>

        <p class="auth-foot">
          还没有账号？<RouterLink to="/register">注册学生账号</RouterLink>
        </p>

        <div class="auth-tip">
          <p>演示账号（点击填入）：</p>
          <p v-for="demo in demos" :key="demo.username">
            <button type="button" class="auth-demo" @click="fill(demo)">
              {{ demo.role }} · {{ demo.username }} / {{ demo.password }}
            </button>
          </p>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth-demo {
  border: 0;
  background: transparent;
  padding: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-a2);
  border-bottom: 1px solid var(--c-line);
}

.auth-demo:hover {
  border-bottom-color: var(--c-a2);
}
</style>
