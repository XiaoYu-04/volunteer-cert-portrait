<script setup>
import { computed, onMounted, ref } from 'vue'
import { listCategories, createCategory, updateCategory, deleteCategory } from '@/api/volunteer'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { formatNumber } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'
import InkField from '@/components/common/InkField.vue'
import InkTable from '@/components/common/InkTable.vue'
import InkButton from '@/components/common/InkButton.vue'
import InkDialog from '@/components/common/InkDialog.vue'
import InkProgress from '@/components/common/InkProgress.vue'

const toast = useToast()
const confirm = useConfirm()

const rows = ref([])
const loading = ref(false)

/** 分类接口一次返回全部，不分页，因此不走 useTable */
async function load() {
  loading.value = true
  try {
    rows.value = await listCategories()
  } catch (err) {
    rows.value = []
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const totalActivities = computed(() => rows.value.reduce((sum, c) => sum + c.activityCount, 0))
const maxActivities = computed(() =>
  rows.value.reduce((max, c) => Math.max(max, c.activityCount), 0),
)
const busiest = computed(() => rows.value.find((c) => c.activityCount === maxActivities.value) || null)

const columns = [
  { key: 'sort', title: '排序', width: '70px', align: 'right' },
  { key: 'name', title: '分类名称', width: '140px' },
  { key: 'code', title: '分类编码', width: '150px' },
  { key: 'activityCount', title: '活动数量', width: '200px' },
  { key: 'remark', title: '说明' },
  { key: 'actions', title: '操作', width: '150px', align: 'right', cellClass: 'col-actions' },
]

/* ---------- 新增 / 编辑 ---------- */
const dialog = ref({ open: false, mode: 'create' })
const form = ref({ id: null, name: '', code: '', sort: 1, remark: '' })
const error = ref('')

function openCreate() {
  form.value = { id: null, name: '', code: '', sort: rows.value.length + 1, remark: '' }
  error.value = ''
  dialog.value = { open: true, mode: 'create' }
}

function openEdit(row) {
  form.value = { id: row.id, name: row.name, code: row.code, sort: row.sort, remark: row.remark }
  error.value = ''
  dialog.value = { open: true, mode: 'edit' }
}

async function submit() {
  const item = form.value
  if (!item.name.trim()) {
    error.value = '请填写分类名称'
    return
  }
  error.value = ''

  const payload = {
    name: item.name.trim(),
    code: item.code.trim(),
    sort: Number(item.sort) || rows.value.length + 1,
    remark: item.remark,
  }

  try {
    if (dialog.value.mode === 'create') {
      await createCategory(payload)
      toast.success('分类已新增')
    } else {
      await updateCategory(item.id, payload)
      toast.success('分类已保存')
    }
    dialog.value.open = false
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------- 删除 ---------- */
async function remove(row) {
  const okToGo = await confirm({
    title: '删除分类',
    message: `删除「${row.name}」后不可恢复。`,
    tone: 'danger',
    confirmText: '删除',
  })
  if (!okToGo) return

  try {
    await deleteCategory(row.id)
    toast.success('分类已删除')
    await load()
  } catch (err) {
    // 分类下仍有活动时后端会拒绝删除，把原因原样提示出来
    toast.error(err.message)
  }
}
</script>

<template>
  <h1 class="console-title">活动分类管理</h1>
  <p class="console-sub">
    维护活动分类，分类下还有活动时不能删除。
  </p>

  <div class="stats">
    <InkStat label="分类总数" :value="rows.length" unit="类" :delta="null" />
    <InkStat label="覆盖活动" :value="totalActivities" unit="场" :delta="null" />
    <InkStat
      :label="`活动最多 · ${busiest?.name || '—'}`"
      :value="maxActivities"
      unit="场"
      :delta="null"
    />
  </div>

  <section class="panel panel-gap">
    <div class="panel-head">
      <h2 class="panel-title">分类列表</h2>
      <span class="panel-extra">
        <InkButton size="sm" variant="primary" @click="openCreate">新增分类</InkButton>
      </span>
    </div>

    <InkTable :columns="columns" :rows="rows" :loading="loading" empty-text="暂无活动分类">
      <template #sort="{ row }">
        <span class="col-num">{{ row.sort }}</span>
      </template>

      <template #name="{ row }">
        <span class="cell-strong">{{ row.name }}</span>
      </template>

      <template #code="{ row }">
        <span class="col-num">{{ row.code }}</span>
      </template>

      <template #activityCount="{ row }">
        <InkProgress
          :value="row.activityCount"
          :max="maxActivities || 1"
          :text="`${formatNumber(row.activityCount)} 场`"
        />
      </template>

      <template #remark="{ row }">
        <span class="cell-mute">{{ row.remark || '—' }}</span>
      </template>

      <template #actions="{ row }">
        <InkButton size="sm" variant="ghost" @click="openEdit(row)">编辑</InkButton>
        <InkButton size="sm" variant="ghost" @click="remove(row)">删除</InkButton>
      </template>
    </InkTable>
  </section>

  <InkDialog
    v-model="dialog.open"
    :title="dialog.mode === 'create' ? '新增活动分类' : '编辑活动分类'"
  >
    <form @submit.prevent="submit">
      <InkField label="分类名称" required :error="error">
        <input v-model.trim="form.name" class="ink-input" type="text" placeholder="如 社区服务" />
      </InkField>

      <InkField label="分类编码" hint="大写英文编码，留空由系统生成">
        <input v-model.trim="form.code" class="ink-input" type="text" placeholder="如 COMMUNITY" />
      </InkField>

      <InkField label="排序" hint="数值越小越靠前">
        <input v-model="form.sort" class="ink-input" type="number" min="1" />
      </InkField>

      <InkField label="说明">
        <textarea
          v-model.trim="form.remark"
          class="ink-textarea"
          placeholder="如 面向社区与居民的常态化服务"
        ></textarea>
      </InkField>
    </form>

    <template #footer>
      <InkButton variant="ghost" @click="dialog.open = false">取消</InkButton>
      <InkButton variant="primary" @click="submit">
        {{ dialog.mode === 'create' ? '确认新增' : '保存修改' }}
      </InkButton>
    </template>
  </InkDialog>
</template>
