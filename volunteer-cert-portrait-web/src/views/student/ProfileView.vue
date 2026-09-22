<script setup>
import { reactive, ref } from 'vue'
import { updateProfile } from '@/api/auth'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'

import InkField from '@/components/common/InkField.vue'
import InkButton from '@/components/common/InkButton.vue'

const toast = useToast()
const user = useUserStore()

const form = reactive({
  name: user.info?.name || '',
  phone: '',
  email: '',
})

const errors = reactive({ name: '', phone: '', email: '' })
const saving = ref(false)

function validate() {
  errors.name = form.name ? '' : '请填写姓名'
  errors.phone = form.phone && !/^1\d{10}$/.test(form.phone) ? '请填写 11 位手机号' : ''
  errors.email = form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '邮箱格式不正确' : ''
  return !errors.name && !errors.phone && !errors.email
}

async function onSubmit() {
  if (!validate()) return

  saving.value = true
  try {
    const data = await updateProfile({ name: form.name, phone: form.phone, email: form.email })
    // 同步 store，顶部导航与页脚的姓名会一起更新
    if (user.info && data) Object.assign(user.info, data)
    toast.success('保存成功')
  } catch (err) {
    toast.error(err.message)
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.name = user.info?.name || ''
  form.phone = ''
  form.email = ''
  errors.name = ''
  errors.phone = ''
  errors.email = ''
}

const quickLinks = [
  { to: '/student/signups', label: '我的报名' },
  { to: '/student/durations', label: '我的服务时长' },
  { to: '/student/portrait', label: '我的公益画像' },
  { to: '/student/notifications', label: '通知公告' },
]
</script>

<template>
  <div class="wrap">
    <header class="page-head">
      <span class="hero-kicker">个人中心</span>
      <h1>个人中心</h1>
      <p class="page-head-sub">
        查看账号信息并维护联系方式。姓名、手机号与邮箱将用于志愿活动通知与服务时长认证。
      </p>
    </header>

    <section class="sec-detail">
      <div class="grid-2">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">账号信息</span>
          </div>

          <div class="account">
            <span class="seal" aria-hidden="true">{{ user.info?.avatarText || '志愿' }}</span>
            <div class="account-main">
              <span class="account-name">{{ user.info?.name || '—' }}</span>
              <span class="account-role">{{ user.info?.roleLabel || '—' }}</span>
            </div>
          </div>

          <dl class="ink-desc desc-one">
            <div class="ink-desc-item">
              <dt class="ink-desc-k">用户名</dt>
              <dd class="ink-desc-v num">{{ user.info?.username || '—' }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">姓名</dt>
              <dd class="ink-desc-v">{{ user.info?.name || '—' }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">角色</dt>
              <dd class="ink-desc-v">{{ user.info?.roleLabel || '—' }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">学院</dt>
              <dd class="ink-desc-v">{{ user.info?.college || '—' }}</dd>
            </div>
            <div class="ink-desc-item">
              <dt class="ink-desc-k">学号</dt>
              <dd class="ink-desc-v num">{{ user.info?.studentNo || '—' }}</dd>
            </div>
          </dl>
        </div>

        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">修改资料</span>
          </div>

          <form @submit.prevent="onSubmit">
            <InkField label="姓名" required :error="errors.name">
              <input v-model.trim="form.name" class="ink-input" type="text" placeholder="真实姓名" />
            </InkField>

            <InkField label="手机号" :error="errors.phone" hint="留空表示不修改，填写后将覆盖原值">
              <input v-model.trim="form.phone" class="ink-input" type="tel" placeholder="11 位手机号" />
            </InkField>

            <InkField label="邮箱" :error="errors.email" hint="用于接收审核结果通知">
              <input
                v-model.trim="form.email"
                class="ink-input"
                type="email"
                placeholder="name@example.edu"
              />
            </InkField>

            <div class="ink-form-actions">
              <InkButton native-type="submit" variant="primary" :disabled="saving">
                {{ saving ? '保存中…' : '保存修改' }}
              </InkButton>
              <InkButton @click="resetForm">还原</InkButton>
            </div>
          </form>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">快捷入口</span>
        </div>
        <div class="quick-links">
          <InkButton v-for="link in quickLinks" :key="link.to" :to="link.to" size="sm">
            {{ link.label }}
          </InkButton>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.sec-detail {
  padding: 36px 0 72px;
}

.account {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 26px;
}

.account-name {
  display: block;
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: 0.06em;
  color: var(--c-ink);
}

.account-role {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  letter-spacing: 0.18em;
  color: var(--c-a2);
}

.desc-one {
  grid-template-columns: 1fr;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}
</style>