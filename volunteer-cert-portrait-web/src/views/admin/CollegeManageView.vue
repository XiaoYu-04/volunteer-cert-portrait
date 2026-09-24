<script setup>
import { computed, onMounted, ref } from 'vue'
import { listColleges, createCollege, deleteCollege, updateCollegeStatus } from '@/api/system'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { formatNumber } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'
import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const toast = useToast()
const confirm = useConfirm()

const rows = ref([])
const loading = ref(false)

/** 学院是字典数据，量级很小，接口一次返回全部、不分页，因此不走 useTable */
async function load() {
  loading.value = true
  try {
    rows.value = await listColleges()
  } catch (err) {
    rows.value = []
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const totalStudents = computed(() => rows.value.reduce((sum, r) => sum + r.studentCount, 0))
const totalOrgs = computed(() => rows.value.reduce((sum, r) => sum + r.orgCount, 0))
/** 新增时的默认排序：排在最后，避免与已有学院并列 */
const nextSort = computed(() => rows.value.reduce((max, r) => Math.max(max, r.sort), 0) + 1)

const columns = [
  { key: 'sort', title: '排序', width: '70px', align: 'right' },
  { key: 'name', title: '学院名称' },
  { key: 'studentCount', title: '学生', width: '90px', align: 'right' },
  { key: 'orgCount', title: '组织', width: '90px', align: 'right' },
  { key: 'status', title: '状态', width: '100px' },
  { key: 'actions', title: '操作', width: '170px', align: 'right', cellClass: 'col-actions' },
]

/* ---------- 新增 ---------- */
const dialog = ref({ open: false })
const form = ref({ name: '', sort: 1 })
const error = ref('')

function openCreate() {
  form.value = { name: '', sort: nextSort.value }
  error.value = ''
  dialog.value = { open: true }
}

async function submit() {
  const name = form.value.name.trim()
  if (!name) {
    error.value = '请填写学院名称'
    return
  }
  error.value = ''

  try {
    await createCollege({ name, sort: Number(form.value.sort) || nextSort.value })
    toast.success('学院已新增')
    dialog.value.open = false
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 停用 / 启用 ----------
   停用不校验占用：学院合并或停招时，硬删会被占用校验挡住，
   只能靠停用让它从注册页的学院下拉里消失，已有数据不受影响。 */
async function toggleStatus(row) {
  // 状态码走 ACTIVE / DISABLED，与用户管理的启停同形（后端 StatusUpdateDTO 的口径），
  // 库里存的是 1 / 0，翻译在服务端做
  const next = row.status === 1 ? 'DISABLED' : 'ACTIVE'
  try {
    await updateCollegeStatus(row.id, next)
    toast.success(next === 'ACTIVE' ? '学院已启用' : '学院已停用')
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 删除 ---------- */
async function remove(row) {
  const okToGo = await confirm({
    title: '删除学院',
    message: `删除后「${row.name}」不再出现在注册页的学院下拉里，确定继续吗？`,
    tone: 'danger',
    confirmText: '删除',
  })
  if (!okToGo) return

  try {
    await deleteCollege(row.id)
    toast.success('学院已删除')
    await load()
  } catch (err) {
    // 学院下仍有学生或组织时后端会拒绝，把原因原样提示出来
    toast.error(err.message)
  }
}
</script>

<template>
  <h1 class="console-title">学院管理</h1>
  <p class="console-sub">维护学院字典，学院下还有学生或组织时不能删除。</p>

  <div class="stats">
    <InkStat label="学院总数" :value="rows.length" unit="个" :delta="null" />
    <InkStat label="覆盖学生" :value="totalStudents" unit="人" :delta="null" />
    <InkStat label="挂靠组织" :value="totalOrgs" unit="个" :delta="null" />
  </div>

  <section class="panel panel-gap">
    <div class="panel-head">
      <span class="panel-title">学院列表</span>
      <span class="panel-extra">
        <span class="panel-note">共 {{ formatNumber(rows.length) }} 个学院</span>
        <InkButton size="sm" variant="primary" @click="openCreate">新增学院</InkButton>
      </span>
    </div>

    <InkTable
      :columns="columns"
      :rows="rows"
      :loading="loading"
      empty-text="暂无学院"
      empty-hint="新增学院后，注册页的学院下拉即可选到它"
    >
      <template #sort="{ row }">
        <span class="col-num">{{ row.sort }}</span>
      </template>

      <template #name="{ row }">
        <span class="cell-strong">{{ row.name }}</span>
      </template>

      <template #studentCount="{ row }">
        <span class="col-num">{{ formatNumber(row.studentCount) }}</span>
      </template>

      <template #orgCount="{ row }">
        <span class="col-num">{{ formatNumber(row.orgCount) }}</span>
      </template>

      <template #status="{ row }">
        <!-- 学院状态与账号状态是同一套语义（1 启用 / 0 停用），
             复用 user_status 字典（正常 / 已停用），不另造一个字典类型 -->
        <StatusTag type="user_status" :value="row.status === 1 ? 'ACTIVE' : 'DISABLED'" />
      </template>

      <template #actions="{ row }">
        <InkButton v-if="row.status === 1" size="sm" variant="ghost" @click="toggleStatus(row)">
          停用
        </InkButton>
        <InkButton v-else size="sm" variant="ghost" @click="toggleStatus(row)">启用</InkButton>
        <InkButton size="sm" variant="ghost" @click="remove(row)">删除</InkButton>
      </template>
    </InkTable>
  </section>

  <InkDialog v-model="dialog.open" title="新增学院">
    <form @submit.prevent="submit">
      <InkField label="学院名称" required :error="error" hint="新增后立即出现在注册页的学院下拉里">
        <input v-model.trim="form.name" class="ink-input" type="text" placeholder="如 材料学院" />
      </InkField>

      <InkField label="排序" hint="数值越小越靠前">
        <input v-model="form.sort" class="ink-input" type="number" min="1" />
      </InkField>
    </form>

    <template #footer>
      <InkButton variant="ghost" @click="dialog.open = false">取消</InkButton>
      <InkButton variant="primary" @click="submit">确认新增</InkButton>
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
</style>
