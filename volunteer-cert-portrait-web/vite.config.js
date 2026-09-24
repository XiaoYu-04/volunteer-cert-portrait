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
      // 无需额外加 apply: 'serve' —— 该插件内部已写死 apply: "serve"（见
      // node_modules/vite-plugin-vue-devtools/dist/vite.js 的 plugin 定义），
      // 构建时本来就不生效；它的 options 里也没有 apply 这个字段。
      vueDevTools(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      rollupOptions: {
        output: {
          // 把 ECharts 与图表配置拆成两个 chunk。
          //
          // 背景（2026-09-24 实测）：ECharts 本来就是懒加载的独立 chunk，
          // **主 chunk 一直只有 ~57 kB**，那条「chunks are larger than 500 kB」
          // 警告说的不是主包。真正的问题只是 options.js 的图表配置（9.9 kB）
          // 被和 ECharts（653 kB）熔进了同一个 662.7 kB 的 chunk ——
          // rolldown 给它起名 `options`，很容易被误读成主包。
          // 拆开后两者可各自缓存，改图表配置不必让用户重下 653 kB。
          //
          // ⚠️ Vite 8 底层是 Rolldown，**不能**用 `manualChunks: { echarts: [...] }`
          // 这种对象写法 —— 只接受函数，传对象会让构建直接失败
          // （Invalid type: Expected Function but received Object）。
          // 这里用 Rolldown 原生的 advancedChunks。
          //
          // 正则用 `[\\/]` 而不是 `/` 匹配路径分隔符：Windows 上模块 id 是反斜杠，
          // 写成 /node_modules\/echarts/ 在 Windows 下匹配不到（rolldown 文档明确警告）。
          advancedChunks: {
            groups: [{ name: 'echarts', test: /node_modules[\\/]echarts/ }],
          },
        },
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
        '/uploads': {
          target: env.VITE_API_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
