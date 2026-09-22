import { computed } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 从路由表派生侧边栏/导航菜单，避免菜单和路由两份定义不同步。
 *
 * @param {string | (() => string)} rootPath 角色路由根路径，如 '/org'。
 *   传 getter 是因为控制台外壳被组织端和学校端共用，根路径要跟着当前路由走。
 * @returns {import('vue').ComputedRef<Array<{ name: string, items: Array<{ path: string, title: string }> }>>}
 */
export function useMenu(rootPath) {
  const router = useRouter()
  const resolve = typeof rootPath === 'function' ? rootPath : () => rootPath

  return computed(() => {
    const base = resolve()
    const root = router.options.routes.find((r) => r.path === base)
    const children = (root?.children || []).filter((c) => c.meta?.menu)

    const groups = []
    children.forEach((child) => {
      const name = child.meta.group || '其他'
      let group = groups.find((g) => g.name === name)
      if (!group) {
        group = { name, items: [] }
        groups.push(group)
      }
      group.items.push({
        path: `${base}/${child.path}`,
        title: child.meta.title,
        name: child.name,
      })
    })
    return groups
  })
}