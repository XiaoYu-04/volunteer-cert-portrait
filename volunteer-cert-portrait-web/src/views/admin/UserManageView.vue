<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  listUsers,
  createUser,
  updateUser,
  updateUserStatus,
  deleteUser,
  listRoles,
} from '@/api/system'
import { useTable } from '@/composables/useTable'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDictStore } from '@/stores/dict'
import { formatNumber, formatDateTime } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'
import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkPagination from '@/components/common/InkPagination.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const toast = useToast()
const confirm = useConfirm()
const dict = useDictStore()

const { rows, total, loading, query, search, load } = useTable(listUsers, {
  defaultQuery: { keyword: '', role: '', status: '' },
})

const roles = ref([])

onMounted(async () => {
  dict.load()
  try {
    roles.value = await listRoles()
  } catch (err) {
    toast.error(err.message)
  }
})

const statusOptions = dict.options('user_status')

const roleName = (code) => roles.value.find((r) => r.code === code)?.name || code

const columns = [
  { key: 'username', title: '用户名', width: '150px' },
  { key: 'name', title: '姓名', width: '100px' },
  { key: 'role', title: '角色', width: '110px' },
  { key: 'phone', title: '手机号', width: '130px' },
  { key: 'email', title: '邮箱' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'lastLoginAt', title: '最近登录', width: '160px' },
  { key: 'actions', title: '操作', width: '190px', align: 'right', cellClass: 'col-actions' },
]

/* ---------- 新增 / 编辑 ---------- */
const dialog = ref({ open: false, mode: 'create' })
const form = ref({ id: null, username: '', name: '', role: 'STUDENT', phone: '', email: '', password: '' })
const errors = ref({})

function openCreate() {
  form.value = {
    id: null,
    username: '',
    name: '',
    role: 'STUDENT',
    phone: '',
    email: '',
    password: '',
  }
  errors.value = {}
  dialog.value = { open: true, mode: 'create' }
}

function openEdit(row) {
  form.value = {
    id: row.id,
    username: row.username,
    name: row.name,
    role: row.role,
    phone: row.phone,
    email: row.email,
    password: '',
  }
  errors.value = {}
  dialog.value = { open: true, mode: 'edit' }
}

function validate() {
  const item = form.value
  const next = {}
  if (dialog.value.mode === 'create' && !item.username.trim()) next.username = '请填写用户名'
  if (!item.name.trim()) next.name = '请填写姓名'
  if (!item.role) next.role = '请选择角色'
  errors.value = next
  return !Object.keys(next).length
}

async function submit() {
  if (!validate()) return
  const item = form.value

  try {
    if (dialog.value.mode === 'create') {
      await createUser({
        username: item.username.trim(),
        name: item.name.trim(),
        role: item.role,
        phone: item.phone.trim(),
        email: item.email.trim(),
        password: item.password.trim() || undefined,
      })
      toast.success('用户已新增')
    } else {
      await updateUser(item.id, {
        name: item.name.trim(),
        role: item.role,
        phone: item.phone.trim(),
        email: item.email.trim(),
      })
      toast.success('用户已保存')
    }
    dialog.value.open = false
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 启用 / 停用 ---------- */
async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (next === 'DISABLED') {
    const okToGo = await confirm({
      title: '停用账号',
      message: `停用后「${row.name}」将无法登录系统，确定继续吗？`,
      tone: 'danger',
      confirmText: '停用',
    })
    if (!okToGo) return
  }

  try {
    await updateUserStatus(row.id, next)
    toast.success(next === 'ACTIVE' ? '账号已启用' : '账号已停用')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 删除 ---------- */
async function remove(row) {
  const okToGo = await confirm({
    title: '删除用户',
    message: `确定删除账号「${row.username}」吗？该操作不可恢复。`,
    tone: 'danger',
    confirmText: '删除',
  })
  if (!okToGo) return

  try {
    await deleteUser(row.id)
    toast.success('用户已删除')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

const roleCount = computed(() => roles.value.reduce((sum, r) => sum + r.userCount, 0))

function resetQuery() {
  query.keyword = ''
  query.role = ''
  query.status = ''
  search()
}
</script>

<template>
  <h1 class="console-title">用户管理</h1>
  <p class="console-sub">
    维护学生、组织管理员与学校管理员三类账号。停用账号将立即失去登录能力，但历史服务记录与画像数据不受影响。
  </p>

  <div class="stats">
    <InkStat
      v-for="role in roles"
      :key="role.code"
      :label="`${role.name}账号`"
      :value="role.userCount"
      unit="人"
      :delta="null"
    />
  </div>

  <section class="panel panel-gap">
    <form class="ink-filter" @submit.prevent="search">
      <InkField label="关键字">
        <input
          v-model.trim="query.keyword"
          class="ink-input"
          type="search"
          placeholder="用户名或姓名"
        />
      </InkField>

      <InkField label="角色">
        <select v-model="query.role" class="ink-select">
          <option value="">全部角色</option>
          <option v-for="role in roles" :key="role.code" :value="role.code">{{ role.name }}</option>
        </select>
      </InkField>

      <InkField label="状态">
        <select v-model="query.status" class="ink-select">
          <option value="">全部状态</option>
          <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </InkField>

      <div class="ink-filter-actions">
        <InkButton native-type="submit" variant="primary" size="sm">查询</InkButton>
        <InkButton size="sm" @click="resetQuery">重置</InkButton>
      </div>
    </form>

    <div class="panel-head">
      <span class="panel-title">账号列表</span>
      <span class="panel-extra">
        <span class="panel-note">
          共 {{ formatNumber(total) }} 个账号 · 全校 {{ formatNumber(roleCount) }} 人
        </span>
        <InkButton size="sm" variant="primary" @click="openCreate">新增用户</InkButton>
      </span>
    </div>

    <InkTable :columns="columns" :rows="rows" :loading="loading" empty-text="没有符合条件的账号">
      <template #username="{ row }">
        <span class="col-num">{{ row.username }}</span>
      </template>

      <template #name="{ row }">
        <span class="cell-strong">{{ row.name }}</span>
      </template>

      <template #role="{ row }">
        <span class="role-chip">{{ roleName(row.role) }}</span>
      </template>

      <template #phone="{ row }">
        <span class="col-num">{{ row.phone || '—' }}</span>
      </template>

      <template #email="{ row }">
        <span class="col-num">{{ row.email || '—' }}</span>
      </template>

      <template #status="{ row }">
        <StatusTag type="user_status" :value="row.status" />
      </template>

      <template #lastLoginAt="{ row }">
        <span class="col-num">{{ formatDateTime(row.lastLoginAt) }}</span>
      </template>

      <template #actions="{ row }">
        <InkButton size="sm" variant="ghost" @click="openEdit(row)">编辑</InkButton>
        <InkButton size="sm" variant="ghost" @click="toggleStatus(row)">
          {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
        </InkButton>
        <InkButton size="sm" variant="ghost" @click="remove(row)">删除</InkButton>
      </template>
    </InkTable>

    <InkPagination v-model:page="query.page" v-model:page-size="query.pageSize" :total="total" />
  </section>

  <InkDialog
    v-model="dialog.open"
    :title="dialog.mode === 'create' ? '新增用户' : '编辑用户'"
  >
    <form @submit.prevent="submit">
      <InkField
        label="用户名"
        :required="dialog.mode === 'create'"
        :error="errors.username"
        :hint="dialog.mode === 'create' ? '登录账号，创建后不可修改' : '登录账号不可修改'"
      >
        <input
          v-model.trim="form.username"
          class="ink-input"
          type="text"
          :disabled="dialog.mode === 'edit'"
          placeholder="如 stu202210138"
        />
      </InkField>

      <div class="ink-form-grid">
        <InkField label="姓名" required :error="errors.name">
          <input v-model.trim="form.name" class="ink-input" type="text" placeholder="真实姓名" />
        </InkField>

        <InkField label="角色" required :error="errors.role">
          <select v-model="form.role" class="ink-select">
            <option v-for="role in roles" :key="role.code" :value="role.code">
              {{ role.name }}
            </option>
          </select>
        </InkField>

        <InkField label="手机号">
          <input v-model.trim="form.phone" class="ink-input" type="text" placeholder="如 138****3300" />
        </InkField>

        <InkField label="邮箱">
          <input v-model.trim="form.email" class="ink-input" type="email" placeholder="如 name@example.edu" />
        </InkField>

        <InkField
          v-if="dialog.mode === 'create'"
          label="初始密码"
          class="span-2"
          hint="留空则使用默认密码 123456，首次登录后请提醒用户修改"
        >
          <input v-model.trim="form.password" class="ink-input" type="text" placeholder="默认 123456" />
        </InkField>
      </div>
    </form>

    <template #footer>
      <InkButton variant="ghost" @click="dialog.open = false">取消</InkButton>
      <InkButton variant="primary" @click="submit">
        {{ dialog.mode === 'create' ? '确认新增' : '保存修改' }}
      </InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.panel-gap {
  margin-top: 36px;
}

.panel-note {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-3);
}

.cell-strong {
  font-family: var(--font-display);
  color: var(--c-ink);
}

.role-chip {
  display: inline-block;
  padding: 2px 8px;
  font-family: var(--font-display);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--c-ink-2);
  border: 1px solid var(--c-line);
  background: #fff;
  white-space: nowrap;
}

.ink-filter-actions {
  display: flex;
  gap: 12px;
  padding-bottom: 2px;
}
</style>