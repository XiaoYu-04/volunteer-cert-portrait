<script setup>
/**
 * 本人改密弹窗。学生端挂在个人中心，组织端/学校端挂在控制台侧栏
 * （这两类管理员没有个人中心页，这里是他们唯一的自助改密入口）。
 */
import { computed, ref, watch } from 'vue'
import { changePassword } from '@/api/auth'
import { useToast } from '@/composables/useToast'

import InkField from '@/components/common/InkField.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const toast = useToast()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const form = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const errors = ref({})
const submitting = ref(false)

// 关闭途径不止「取消」一种（遮罩、Esc、右上角 ×），统一在这里清空，
// 避免上一次输入的明文口令留在输入框里被下一个人看到
watch(
  () => props.modelValue,
  (open) => {
    if (!open) reset()
  },
)

function reset() {
  form.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  errors.value = {}
}

function validate() {
  const item = form.value
  const next = {}
  if (!item.oldPassword) next.oldPassword = '请输入原密码'
  if (!item.newPassword) {
    next.newPassword = '请输入新密码'
  } else if (item.newPassword.length < 6 || item.newPassword.length > 32) {
    next.newPassword = '密码长度需为 6–32 位'
  } else if (item.newPassword === item.oldPassword) {
    next.newPassword = '新密码不能与原密码相同'
  }
  if (!item.confirmPassword) {
    next.confirmPassword = '请再次输入新密码'
  } else if (item.confirmPassword !== item.newPassword) {
    next.confirmPassword = '两次输入的密码不一致'
  }
  errors.value = next
  return !Object.keys(next).length
}

async function submit() {
  if (!validate()) return

  submitting.value = true
  try {
    await changePassword({
      oldPassword: form.value.oldPassword,
      newPassword: form.value.newPassword,
    })
    // 后端只踢其它设备的会话，当前会话仍然有效：
    // 所以这里不清 token、不跳登录页，关掉弹窗即可继续用
    toast.success('密码已修改')
    reset()
    visible.value = false
  } catch (err) {
    toast.error(err.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <InkDialog v-model="visible" title="修改密码">
    <form @submit.prevent="submit">
      <InkField label="原密码" required :error="errors.oldPassword">
        <input
          v-model="form.oldPassword"
          class="ink-input"
          type="password"
          autocomplete="current-password"
          placeholder="当前登录密码"
        />
      </InkField>

      <InkField
        label="新密码"
        required
        :error="errors.newPassword"
        hint="6–32 位，建议字母与数字混排"
      >
        <input
          v-model="form.newPassword"
          class="ink-input"
          type="password"
          autocomplete="new-password"
          placeholder="6–32 位"
        />
      </InkField>

      <InkField label="确认新密码" required :error="errors.confirmPassword">
        <input
          v-model="form.confirmPassword"
          class="ink-input"
          type="password"
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </InkField>

      <p class="pwd-note">修改成功后其它设备上的登录会被强制退出，当前页面不受影响。</p>
    </form>

    <template #footer>
      <InkButton variant="ghost" @click="visible = false">取消</InkButton>
      <InkButton variant="primary" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认修改' }}
      </InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.pwd-note {
  margin: 18px 0 0;
  padding: 12px 14px;
  border: 1px dashed var(--c-line);
  font-size: 12px;
  line-height: 1.9;
  color: var(--c-ink-3);
}
</style>
