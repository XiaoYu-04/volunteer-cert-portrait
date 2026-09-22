/**
 * 轻提示。模块级状态即单例，任意组件 import 后拿到的都是同一份队列。
 * 由 App.vue 里挂载的 InkToast 负责渲染。
 */
import { ref } from 'vue'

const items = ref([])
let seq = 0

function push(message, tone = 'info', duration = 2600) {
  const id = ++seq
  items.value.push({ id, message, tone })
  setTimeout(() => {
    items.value = items.value.filter((item) => item.id !== id)
  }, duration)
  return id
}

export function useToast() {
  return {
    items,
    toast: push,
    success: (message, duration) => push(message, 'ok', duration),
    error: (message, duration) => push(message, 'bad', duration),
    warn: (message, duration) => push(message, 'warn', duration),
    info: (message, duration) => push(message, 'info', duration),
  }
}