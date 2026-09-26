import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setupRouterGuard } from './router/guard'
import perm from './directives/perm'
import reveal from './directives/reveal'

// 样式按层引入：令牌 → 结构 → 组件 → 动效。顺序不能颠倒。
import './styles/tokens.css'
import './styles/base.css'
import './styles/ink.css'
import './styles/motion.css'

const app = createApp(App)

app.use(createPinia())

// 守卫里会用到 store，必须在 pinia 装好之后再挂
setupRouterGuard(router)
app.use(router)

app.directive('perm', perm)
app.directive('reveal', reveal)

app.mount('#app')