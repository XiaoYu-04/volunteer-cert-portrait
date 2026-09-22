<script setup>
import { computed, onMounted, ref } from 'vue'
import { listRoles } from '@/api/system'
import { useToast } from '@/composables/useToast'
import { formatNumber } from '@/utils/format'

import InkStat from '@/components/common/InkStat.vue'

const toast = useToast()

const roles = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    roles.value = await listRoles()
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
})

const totalUsers = computed(() => roles.value.reduce((sum, r) => sum + r.userCount, 0))
const totalPerms = computed(() =>
  roles.value.reduce((sum, r) => sum + (r.perms?.length || 0), 0),
)
</script>

<template>
  <h1 class="console-title">角色管理</h1>
  <p class="console-sub">
    系统采用三角色固定权限模型，角色不可新增或删除。权限标识遵循「模块:资源:动作」三段式，
    由后端在接口层校验，前端据此控制菜单与按钮的可见性。
  </p>

  <div class="stats">
    <InkStat label="角色数量" :value="roles.length" unit="个" :delta="null" />
    <InkStat label="账号总数" :value="totalUsers" unit="人" :delta="null" />
    <InkStat label="权限标识" :value="totalPerms" unit="项" :delta="null" />
  </div>

  <section v-for="role in roles" :key="role.code" class="panel panel-gap">
    <div class="panel-head">
      <span class="panel-title">{{ role.name }}</span>
      <span class="panel-extra">
        <span class="role-code num">{{ role.code }}</span>
        <span class="role-count">
          <b class="num">{{ formatNumber(role.userCount) }}</b> 人
        </span>
      </span>
    </div>

    <p class="role-remark">{{ role.remark }}</p>

    <h3 class="perm-head">权限标识（{{ role.perms?.length || 0 }}）</h3>
    <ul class="perm-list">
      <li v-for="perm in role.perms || []" :key="perm" class="perm-tag">{{ perm }}</li>
    </ul>
  </section>

  <p v-if="!loading && !roles.length" class="role-empty">暂无可展示的角色</p>
</template>

<style scoped>
.panel-gap {
  margin-top: 36px;
}

.role-code {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--c-ink-2);
  border: 1px solid var(--c-line);
  background: #fff;
}

.role-count {
  font-size: 13px;
  color: var(--c-ink-3);
}

.role-count b {
  color: var(--c-ink);
}

.role-remark {
  font-size: 14px;
  line-height: 1.9;
  color: var(--c-ink-2);
}

.perm-head {
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--c-line-2);
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.08em;
  color: var(--c-ink-3);
  font-weight: 400;
}

.perm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

/* 方形小标签，与零圆角的墨线语言一致 */
.perm-tag {
  padding: 4px 10px;
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--c-ink-2);
  border: 1px solid var(--c-line);
  background: #fff;
  white-space: nowrap;
}

.role-empty {
  margin-top: 36px;
  font-size: 14px;
  color: var(--c-ink-3);
}
</style>