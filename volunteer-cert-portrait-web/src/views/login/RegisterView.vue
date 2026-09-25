<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getColleges, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import InkField from '@/components/common/InkField.vue'

const router = useRouter()
const user = useUserStore()
const toast = useToast()

const form = reactive({
  username: '',
  name: '',
  studentNo: '',
  college: '',
  password: '',
  confirm: '',
  phone: '',
  email: '',
})

const errors = reactive({
  username: '',
  name: '',
  studentNo: '',
  college: '',
  password: '',
  confirm: '',
  phone: '',
  email: '',
})
const loading = ref(false)

/* 学院选项来自免登录接口。刻意不做前端兜底列表：学院是管理员可增删的字典数据，
   写死在代码里就等于造出第二份真相；接口挂了宁可下拉为空、提示加载失败。 */
const colleges = ref([])

const RE_USERNAME = /^[a-zA-Z0-9_]{4,20}$/
/** 学号只限「纯数字 + 长度区间」，刻意不写死位数：库内现有形态实测三种并存
    （20230001 八位 / 2023100001 十位 / S000011 占位），
    固定位数会把扩量数据判成非法。 */
const RE_STUDENT_NO = /^[0-9]{4,20}$/
/** 中国大陆手机号：11 位，1 开头，第二位 3-9 */
const RE_PHONE = /^1[3-9]\d{9}$/
const RE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate() {
  errors.username = RE_USERNAME.test(form.username) ? '' : '用户名为 4-20 位字母、数字或下划线'
  errors.name = form.name.trim() ? '' : '请输入姓名'
  errors.studentNo = RE_STUDENT_NO.test(form.studentNo) ? '' : '学号为 4-20 位数字'
  errors.college = form.college ? '' : '请选择学院'
  errors.password = form.password.length >= 6 ? '' : '密码至少 6 位'
  errors.confirm = form.confirm === form.password ? '' : '两次输入的密码不一致'
  errors.phone = RE_PHONE.test(form.phone) ? '' : '请输入 11 位手机号'
  errors.email = RE_EMAIL.test(form.email) ? '' : '请输入有效的邮箱地址'

  return !Object.values(errors).some(Boolean)
}

async function loadColleges() {
  try {
    colleges.value = await getColleges()
  } catch (err) {
    toast.error(err.message || '学院列表加载失败')
  }
}

onMounted(loadColleges)

async function onSubmit() {
  if (!validate() || loading.value) return
  loading.value = true
  try {
    const data = await register({
      username: form.username,
      name: form.name,
      studentNo: form.studentNo,
      college: form.college,
      password: form.password,
      phone: form.phone,
      email: form.email,
    })
    // 注册接口直接返回会话，免去二次登录
    user.applySession(data)
    toast.success('注册成功')
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

      <p class="auth-aside-quote">注册后即可<em>报名</em>活动，<br />服务时长经审核后计入公益画像</p>

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

          <InkField label="学号" required :error="errors.studentNo" hint="4-20 位数字">
            <input
              v-model.trim="form.studentNo"
              class="ink-input"
              type="text"
              inputmode="numeric"
              autocomplete="off"
            />
          </InkField>

          <InkField label="学院" required :error="errors.college">
            <select v-model="form.college" class="ink-select">
              <option value="">请选择学院</option>
              <option v-for="c in colleges" :key="c.value" :value="c.value">{{ c.label }}</option>
            </select>
          </InkField>

          <InkField label="密码" required :error="errors.password" hint="至少 6 位">
            <input v-model="form.password" class="ink-input" type="password" autocomplete="new-password" />
          </InkField>

          <InkField label="确认密码" required :error="errors.confirm">
            <input v-model="form.confirm" class="ink-input" type="password" autocomplete="new-password" />
          </InkField>

          <InkField label="手机号" required :error="errors.phone" hint="11 位手机号">
            <input
              v-model.trim="form.phone"
              class="ink-input"
              type="tel"
              inputmode="numeric"
              maxlength="11"
              autocomplete="tel"
            />
          </InkField>

          <InkField label="邮箱" required :error="errors.email">
            <input v-model.trim="form.email" class="ink-input" type="email" autocomplete="email" />
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
