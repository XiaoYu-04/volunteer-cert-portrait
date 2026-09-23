import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 注意：vite.config.js 里 process.env 拿不到 .env 文件里的变量，必须用 loadEnv 读。
  // 第三个参数传 '' 是为了把 VITE_ 前缀之外的变量也一并加载。
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      vue(),
      vueDevTools(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: {
        // 后端 volunteer-cert-portrait-server 就绪后，把 .env 的
        // VITE_USE_MOCK 改成 false，请求就会经这里代理到后端。
        // 默认打本机 8080；要和别人联调时在 .env.local 里设
        // VITE_API_TARGET 指向对方机器（.env.local 不入库）。
        '/api': {
          target: env.VITE_API_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
