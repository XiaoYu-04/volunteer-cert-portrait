<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  listUsers,
  createUser,
  updateUser,
  updateUserStatus,
  deleteUser,
  resetUserPassword,
  listRoles,
  listColleges,
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

// 学院下拉：只在「新增学生」时用得上。
// 数据源取管理端这份列表并自行筛启用项，而不是复用注册页那份 /v1/auth/colleges ——
// 管理端就该走管理端接口；两者读的是同一份字典，停用项在两边都选不到。
const colleges = ref([])

onMounted(async () => {
  dict.load()
  try {
    roles.value = await listRoles()
  } catch (err) {
    toast.error(err.message)
  }
  try {
    colleges.value = (await listColleges()).filter((c) => c.status !== 0)
  } catch {
    // 拉不到就只影响「新增学生」这一条路径，其余功能照常，因此不弹错误提示；
    // 真要新增学生会因为选不到学院被 validate() 拦下，不会静默建出没有学院的档案。
    colleges.value = []
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
  { key: 'actions', title: '操作', width: '250px', align: 'right', cellClass: 'col-actions' },
]

/* ---------- 新增 / 编辑 ---------- */
const dialog = ref({ open: false, mode: 'create' })
const form = ref({
  id: null,
  username: '',
  name: '',
  role: 'STUDENT',
  college: '',
  phone: '',
  email: '',
  password: '',
})
const errors = ref({})

/**
 * 学生档案挂在学院下，所以只有「新增学生」才要求选学院 —— 后端 UserServiceImpl
 * 是同一个条件、同一句文案。编辑不带这个字段：改角色不改档案，学院落在
 * student_info 上，那条写路径是补录学生档案，不在这个表单里。
 */
const needCollege = computed(() => dialog.value.mode === 'create' && form.value.role === 'STUDENT')

function openCreate() {
  form.value = {
    id: null,
    username: '',
    name: '',
    role: 'STUDENT',
    college: '',
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
    // 用户列表不返回学院（它在 student_info 上），编辑态也不提交它
    college: '',
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
  if (needCollege.value && !item.college) next.college = '请选择学院'
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
        // 非学生不带这个字段：后端只在学生角色下读它
        college: needCollege.value ? item.college : undefined,
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

/* ---------- 重置口令 ---------- */
// 不复用新增/编辑的 form：重置只提交一个 password，
// 混在同一份表单里容易把其它字段一起带出去
const resetDialog = ref({ open: false, id: null, name: '', password: '', error: '' })

function openReset(row) {
  resetDialog.value = { open: true, id: row.id, name: row.name, password: '', error: '' }
}

async function submitReset() {
  const item = resetDialog.value
  const value = item.password.trim()
  // 留空表示恢复默认口令 123456；填了则与本人改密保持同一套长度策略
  if (value && (value.length < 6 || value.length > 32)) {
    item.error = '密码长度需为 6–32 位'
    return
  }
  item.error = ''

  try {
    await resetUserPassword(item.id, { password: value })
    toast.success('密码已重置')
    resetDialog.value.open = false
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
      message: `停用后「${row.name}」无法登录。`,
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
    message: `删除「${row.username}」后不可恢复。`,
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
    维护三类账号的资料与启用状态。
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
        <InkButton size="sm" variant="ghost" @click="openReset(row)">重置密码</InkButton>
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

        <InkField v-if="needCollege" label="学院" required :error="errors.college">
          <select v-model="form.college" class="ink-select">
            <option value="">请选择学院</option>
            <option v-for="c in colleges" :key="c.id" :value="c.name">{{ c.name }}</option>
          </select>
        </InkField>

        <InkField label="手机号">
          <input v-model.trim="form.phone" class="ink-input" type="text" placeholder="如 13800000003" />
        </InkField>

        <InkField label="邮箱">
          <input v-model.trim="form.email" class="ink-input" type="email" placeholder="如 name@example.edu" />
        </InkField>

        <InkField
          v-if="dialog.mode === 'create'"
          label="初始密码"
          class="span-2"
          hint="留空则使用默认密码 123456"
        >
          <input
            v-model.trim="form.password"
            class="ink-input"
            type="password"
            autocomplete="new-password"
            placeholder="默认 123456"
          />
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

  <!-- 重置口令单独一个弹窗：目标与编辑用户不同，也避免误改其它字段 -->
  <InkDialog v-model="resetDialog.open" title="重置密码">
    <form @submit.prevent="submitReset">
      <InkField label="新密码" :error="resetDialog.error" hint="留空则重置为默认密码 123456">
        <input
          v-model.trim="resetDialog.password"
          class="ink-input"
          type="password"
          autocomplete="new-password"
          placeholder="默认 123456"
        />
      </InkField>
      <p class="reset-note">
        重置后「{{ resetDialog.name }}」的登录态立即失效，需用新口令重新登录。
      </p>
    </form>

    <template #footer>
      <InkButton variant="ghost" @click="resetDialog.open = false">取消</InkButton>
      <InkButton variant="primary" @click="submitReset">确认重置</InkButton>
    </template>
  </InkDialog>
</template>

<style scoped>
.panel-gap {
  margin-top: 36px;
}

.reset-note {
  margin: 18px 0 0;
  padding: 12px 14px;
  border: 1px dashed var(--c-line);
  font-size: 12px;
  line-height: 1.9;
  color: var(--c-ink-3);
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
