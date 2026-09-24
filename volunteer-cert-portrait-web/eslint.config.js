import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import pluginOxlint from 'eslint-plugin-oxlint'
import skipFormatting from 'eslint-config-prettier/flat'

export default defineConfig([
  {
    name: 'app/files-to-lint',
    files: ['**/*.{vue,js,mjs,jsx}'],
  },

  // `**/.tmp-*/**` 与 .gitignore 里的 `.tmp-*` 规则对应：那是本地临时验证脚本
  // （如 .tmp-verify/verify-text.mjs），不该被 lint —— 否则本地跑过一次验证脚本，
  // 之后 `npm run lint` 就一直报错，看着像仓库坏了。
  globalIgnores(['**/dist/**', '**/dist-ssr/**', '**/coverage/**', '**/.tmp-*/**']),

  {
    languageOptions: {
      globals: {
        ...globals.browser,
      },
    },
  },

  {
    // 构建期脚本与配置文件跑在 Node 里，不是浏览器
    name: 'app/node-scripts',
    files: ['scripts/**/*.{js,mjs}', 'vite.config.js', 'eslint.config.js'],
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },

  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],

  ...pluginOxlint.buildFromOxlintConfigFile('.oxlintrc.json'),

  skipFormatting,
])
