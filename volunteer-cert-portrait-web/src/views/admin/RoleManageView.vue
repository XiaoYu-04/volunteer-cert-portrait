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
    查看三个角色的权限清单，角色不可增删。
  </p>

  <div class="stats">
    <InkStat label="角色数量" :value="roles.length" unit="个" :delta="null" />
    <InkStat label="账号总数" :value="totalUsers" unit="人" :delta="null" />
    <InkStat label="权限标识" :value="totalPerms" unit="项" :delta="null" />
  </div>

  <section v-for="role in roles" :key="role.code" class="panel panel-gap">
    <div class="panel-head">
      <h2 class="panel-title">{{ role.name }}</h2>
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

  <p v-if="!loading && !roles.length" class="role-empty">暂无角色</p>
</template>

<style scoped>
/* 面板标题由展示用 span 换成 h2；h2 浏览器默认加粗，这里保持原常规字重 */

.role-code {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--c-ink-2);
  border: 1px solid var(--c-line);
  background: var(--c-panel);
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
  color: var(--c-ink-2);
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
  background: var(--c-panel);
  white-space: nowrap;
}

.role-empty {
  margin-top: var(--sp-7);
  font-size: 14px;
  color: var(--c-ink-3);
}
</style>
