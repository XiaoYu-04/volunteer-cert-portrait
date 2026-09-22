/**
 * 字典 store —— 状态枚举的「英文码 → 中文标签 + 色调」翻译层。
 *
 * 架构文档 3.6 明确要求状态机「统一英文码存库 + 字典翻译」，因此：
 *   - 后端存的是 DRAFT / PENDING / APPROVED 这类英文码
 *   - 前端展示一律经这里翻译，页面里不出现硬编码的中文状态字符串
 *
 * 静态定义即前端契约，开箱可用；后端 sys_dict 就绪后调 load() 会用接口数据
 * 覆盖同名条目，页面代码无需改动。
 */
import { defineStore } from 'pinia'
import { get } from '@/utils/request'

/** 各状态机的取值、中文标签与色调 */
export const DICT_DEFS = {
  // 活动状态
  activity_status: [
    { value: 'DRAFT', label: '草稿', tone: 'mute' },
    { value: 'PUBLISHED', label: '已发布', tone: 'ok' },
    { value: 'CLOSED', label: '已结束', tone: 'info' },
    { value: 'CANCELED', label: '已取消', tone: 'bad' },
  ],
  // 报名状态
  signup_status: [
    { value: 'PENDING', label: '待审核', tone: 'warn' },
    { value: 'APPROVED', label: '已通过', tone: 'ok' },
    { value: 'REJECTED', label: '已驳回', tone: 'bad' },
    { value: 'CANCELED', label: '已取消', tone: 'mute' },
    { value: 'COMPLETED', label: '已完成', tone: 'info' },
  ],
  // 签到状态。码值与 sql/02_schema.sql 的 attendance_record.status 注释一致
  attendance_status: [
    { value: 'NOT_SIGNED', label: '未签到', tone: 'mute' },
    { value: 'SIGNED_IN', label: '已签到', tone: 'info' },
    { value: 'SIGNED_OUT', label: '已签退', tone: 'ok' },
    { value: 'ABNORMAL', label: '异常', tone: 'warn' },
    { value: 'ABSENT', label: '缺勤', tone: 'bad' },
  ],
  // 服务时长状态
  duration_status: [
    { value: 'PENDING_SUBMIT', label: '待提交', tone: 'mute' },
    { value: 'PENDING_AUDIT', label: '待审核', tone: 'warn' },
    { value: 'APPROVED', label: '已通过', tone: 'ok' },
    { value: 'REJECTED', label: '已驳回', tone: 'bad' },
  ],
  // 组织资质状态
  org_status: [
    { value: 'PENDING', label: '待审核', tone: 'warn' },
    { value: 'APPROVED', label: '已通过', tone: 'ok' },
    { value: 'REJECTED', label: '已驳回', tone: 'bad' },
    { value: 'DISABLED', label: '已停用', tone: 'mute' },
  ],
  // 账号状态
  user_status: [
    { value: 'ACTIVE', label: '正常', tone: 'ok' },
    { value: 'DISABLED', label: '已停用', tone: 'mute' },
  ],
  // 审核动作。注意与状态值的 APPROVED / REJECTED 不同形
  audit_action: [
    { value: 'SUBMIT', label: '提交', tone: 'info' },
    { value: 'APPROVE', label: '通过', tone: 'ok' },
    { value: 'REJECT', label: '驳回', tone: 'bad' },
  ],
  // 通知类型。码值取自 sql/03_init_data.sql 的 notification_type 字典种子
  notification_type: [
    { value: 'SIGNUP', label: '报名结果', tone: 'ok' },
    { value: 'DURATION', label: '时长审核', tone: 'warn' },
    { value: 'SYSTEM', label: '系统公告', tone: 'info' },
  ],
}

export const useDictStore = defineStore('dict', {
  state: () => ({
    /** type → 条目数组 */
    map: Object.fromEntries(
      Object.entries(DICT_DEFS).map(([k, v]) => [k, v.map((item) => ({ ...item }))]),
    ),
    loaded: false,
  }),

  getters: {
    /** 取某类型的全部条目 */
    options: (state) => (type) => state.map[type] || [],
  },

  actions: {
    /** 查标签，未知码原样返回，避免页面出现空白 */
    label(type, value) {
      const hit = (this.map[type] || []).find((item) => item.value === value)
      return hit ? hit.label : value || '—'
    },

    /** 查色调，用于 StatusTag */
    tone(type, value) {
      const hit = (this.map[type] || []).find((item) => item.value === value)
      return hit ? hit.tone : 'mute'
    },

    /** 后端 sys_dict 就绪后调用；失败时静默保留静态定义，不阻塞页面 */
    async load() {
      if (this.loaded) return
      try {
        const data = await get('/v1/system/dicts')
        Object.entries(data || {}).forEach(([type, items]) => {
          if (Array.isArray(items) && items.length) {
            this.map[type] = items.map((item) => ({
              value: item.value,
              label: item.label,
              tone: item.tone || 'mute',
            }))
          }
        })
        this.loaded = true
      } catch {
        // 字典接口不可用不影响功能，静态定义已经够用
      }
    },
  },
})