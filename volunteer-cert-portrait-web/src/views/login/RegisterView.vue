<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import InkField from '@/components/common/InkField.vue'

const router = useRouter()
const user = useUserStore()
const toast = useToast()

const form = reactive({
  username: '',
  name: '',
  password: '',
  confirm: '',
  phone: '',
  email: '',
})

const errors = reactive({ username: '', name: '', password: '', confirm: '' })
const loading = ref(false)

function validate() {
  errors.username = /^[a-zA-Z0-9_]{4,20}$/.test(form.username)
    ? ''
    : '用户名为 4-20 位字母、数字或下划线'
  errors.name = form.name.trim() ? '' : '请输入姓名'
  errors.password = form.password.length >= 6 ? '' : '密码至少 6 位'
  errors.confirm = form.confirm === form.password ? '' : '两次输入的密码不一致'
  return !errors.username && !errors.name && !errors.password && !errors.confirm
}

async function onSubmit() {
  if (!validate() || loading.value) return
  loading.value = true
  try {
    const data = await register({
      username: form.username,
      name: form.name,
      password: form.password,
      phone: form.phone,
      email: form.email,
    })
    // 注册接口直接返回会话，免去二次登录
    user.token = data.token
    user.info = data.user
    toast.success('注册成功，欢迎加入')
    router.replace(user.homePath)
  } catch (err) {
    toast.error(err.message || '注册失败')
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

      <p class="auth-aside-quote">注册后即可<em>报名</em>活动，<br />服务时长经审核计入公益画像</p>

      <p class="auth-aside-foot">VOLUNTEER SERVICE · VCP</p>
    </aside>

    <main class="auth-main">
      <div class="auth-card">
        <h1 class="auth-title">注册</h1>
        <p class="auth-sub">仅开放学生自助注册，组织与管理员账号由学校统一分配</p>

        <form novalidate @submit.prevent="onSubmit">
          <InkField label="用户名" required :error="errors.username" hint="4-20 位字母、数字或下划线">
            <input v-model.trim="form.username" class="ink-input" type="text" autocomplete="username" />
          </InkField>

          <InkField label="姓名" required :error="errors.name">
            <input v-model.trim="form.name" class="ink-input" type="text" autocomplete="name" />
          </InkField>

          <InkField label="密码" required :error="errors.password" hint="至少 6 位">
            <input v-model="form.password" class="ink-input" type="password" autocomplete="new-password" />
          </InkField>

          <InkField label="确认密码" required :error="errors.confirm">
            <input v-model="form.confirm" class="ink-input" type="password" autocomplete="new-password" />
          </InkField>

          <InkField label="手机号" hint="选填，用于接收活动通知">
            <input v-model.trim="form.phone" class="ink-input" type="tel" />
          </InkField>

          <InkField label="邮箱" hint="选填">
            <input v-model.trim="form.email" class="ink-input" type="email" />
          </InkField>

          <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
            {{ loading ? '注册中…' : '注 册' }}
          </button>
        </form>

        <p class="auth-foot">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
      </div>
    </main>
  </div>
</template>