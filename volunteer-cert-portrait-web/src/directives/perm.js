import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令。
 *
 *   <button v-perm="'volunteer:activity:create'">发布活动</button>
 *   <button v-perm="['a:b:c', 'd:e:f']">任一命中即显示</button>
 *
 * 不满足时直接把元素从 DOM 移除（而非隐藏），避免用户改样式后仍可点击。
 * 注意：权限在会话内不会变化，因此只做挂载时判断，不做响应式更新。
 */
export const perm = {
  mounted(el, binding) {
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!required.length) return

    const user = useUserStore()
    if (!required.some((p) => user.hasPerm(p))) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default perm