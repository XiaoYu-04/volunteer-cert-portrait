/**
 * 确认框。返回一个函数，调用后得到 Promise<boolean>：
 *
 *   const confirm = useConfirm()
 *   if (await confirm({ message: '确定删除该活动？' })) { ... }
 *
 * 由 App.vue 里挂载的 InkConfirm 负责渲染。
 */
import { ref } from 'vue'

export const confirmState = ref(null)

export function useConfirm() {
  return (options = {}) =>
    new Promise((resolve) => {
      confirmState.value = {
        title: options.title || '确认操作',
        message: options.message || '',
        confirmText: options.confirmText || '确定',
        cancelText: options.cancelText || '取消',
        // default | danger —— danger 用于删除等不可逆操作
        tone: options.tone || 'default',
        resolve,
      }
    })
}

/** 由 InkConfirm 调用，收尾并唤醒等待中的 Promise */
export function settleConfirm(result) {
  const state = confirmState.value
  confirmState.value = null
  if (state && state.resolve) state.resolve(result)
}