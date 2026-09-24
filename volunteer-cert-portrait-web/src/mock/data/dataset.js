/**
 * 冻结模拟数据集。
 *
 * 聚合指标自原型 styles_12-chinese-ink.html 的 OD_DATA 原样继承，改动任一数值
 * 请同步校验下列等式，否则各页面之间会出现对不上的数字：
 *
 *   学院时长合计 = 86,420 = 累计志愿时长
 *   活动类型合计 =    386 = 活动总数
 *   公益画像合计 = 12,480 = 报名总人数
 *   月度活动合计 =    386 = 活动总数
 *   月度时长合计 = 86,420 = 累计志愿时长
 *   签到率     = 2,290 / 2,480 = 92.3%
 *   审核通过率 = 2,142 / 2,418 = 88.6%
 *
 * 原型只给了 6 场活动、5 条公告。列表页需要分页，因此下面按同一风格把活动
 * 扩充到 24 场；扩充部分不参与上面的聚合校验，聚合数字仍以冻结值为准。
 *
 * 有两处**没有照搬原型、已按后端口径修正**（都在下面的分段注释里写明了理由）：
 *   1. 活动分类（types / categories）改用库里 activity_category 的名称与顺序（待办 A12）；
 *   2. 画像标签（profiles）由 6 类补齐到 8 类（对齐后端 PortraitTagEnum，待办 B20-4），
 *      合计仍为 12,480 —— 新增两类时**类型标签合计 6,840 保持不变**，只是重新分摊。
 */

// 带 .js 后缀：本文件会被 scripts/verify-mock-data.mjs 用 Node 直接加载，
// Node 的 ESM 解析器不像 Vite 那样会自动补扩展名
import { loadPersistedUsers } from './_helpers.js'

/* ============================================================
   一、聚合指标（冻结）
   ============================================================ */

export const stats = [
  { key: 'activities', label: '活动总数', value: 386, unit: '场', delta: 12.0, trend: 'up' },
  { key: 'enrolled', label: '报名总人数', value: 12480, unit: '人', delta: 8.4, trend: 'up' },
  { key: 'hours', label: '累计志愿时长', value: 86420, unit: '小时', delta: 15.2, trend: 'up' },
  { key: 'monthNew', label: '本月新增活动', value: 42, unit: '场', delta: 6.1, trend: 'up' },
  { key: 'signRate', label: '签到率', value: 92.3, unit: '%', delta: 1.4, trend: 'up' },
  { key: 'passRate', label: '时长审核通过率', value: 88.6, unit: '%', delta: -0.8, trend: 'down' },
]

export const trend = [
  { month: '2025-01', count: 28, hours: 6280 },
  { month: '2025-02', count: 18, hours: 4040 },
  { month: '2025-03', count: 52, hours: 11640 },
  { month: '2025-04', count: 44, hours: 9860 },
  { month: '2025-05', count: 38, hours: 8500 },
  { month: '2025-06', count: 24, hours: 5380 },
  { month: '2025-07', count: 12, hours: 2690 },
  { month: '2025-08', count: 15, hours: 3360 },
  { month: '2025-09', count: 56, hours: 12540 },
  { month: '2025-10', count: 48, hours: 10760 },
  { month: '2025-11', count: 34, hours: 7620 },
  { month: '2025-12', count: 17, hours: 3750 },
]

/**
 * 活动分类与活动数（冻结值，合计 386）。
 *
 * 名称与顺序**以后端 `activity_category` 为准**（待办 A12）：库里是
 * 校园服务 / 社区服务 / 环保公益 / 大型赛事 / 助老服务 / 文化传播，id 即 1~6。
 * 原型用的是另一套名字与顺序（社区服务 / 环保行动 / 支教助学 / 大型赛会 /
 * 校园服务 / 敬老助残），接上真实接口后分类筛选器与分类管理页会整体换文案，
 * 因此这里直接改成库里的那套，顺序也按 sort 排。
 *
 * code 与 `sql/06_backend_gap_fix2.sql` 回填的编码一致，写在每项里而不是另起一个
 * 按下标对齐的数组 —— 下标对齐的数组在分类改名或调整顺序时会静默错位（B18 的脆弱点）。
 *
 * remark 逐字取自 `sql/07_demo_scale.sql` 第一节的回填（真库 6 条都有值），
 * 让分类管理页的「说明」列在 mock 与真后端下看到的是同一套文案。
 */
export const types = [
  { name: '校园服务', code: 'CAMPUS', value: 54, remark: '校园内的日常服务：迎新、图书馆整理、校园导览、秩序维护等' },
  { name: '社区服务', code: 'COMMUNITY', value: 96, remark: '面向周边社区的服务：便民维修、社区课堂、邻里互助等' },
  { name: '环保公益', code: 'ENVIRONMENT', value: 74, remark: '环境保护类：河道清理、垃圾分类宣传、绿化养护、植树护绿等' },
  { name: '大型赛事', code: 'EVENT', value: 58, remark: '大型赛事与活动的服务保障：马拉松、球类联赛、运动会等' },
  { name: '助老服务', code: 'ELDERLY', value: 42, remark: '面向老年人的服务：敬老院陪伴、老年手机课堂、上门慰问等' },
  { name: '文化传播', code: 'CULTURE', value: 62, remark: '文化传播类：非遗宣传、文化节讲解、博物馆导览、校史宣讲等' },
]

/**
 * 学院时长排名（`/analytics/colleges` 的数据源，喂看板图四、首页图三与排行列表）。
 *
 * 只列**有认证时长的学院**：真库那边是 `GROUP BY si.college` 现算，没有学生的
 * 学院根本不会出现在结果里，所以这里也不列「电子信息学院」（它在 mock 里既无
 * 学生也无组织）。名字取自下面的 `collegeDict`，不再是原型里那 8 个学院 ——
 * 原型那份含「环境学院 / 文学院 / 医学院 / 体育学院」，与系统里的学院字典对不上，
 * 看板显示「8 个学院」而学院管理页只有 5 个，属于同一屏自相矛盾。
 *
 * `hours` 合计仍是 86,420（verify:mock 钉着「学院时长合计 = 累计志愿时长」），
 * 只是把原型 8 行的时长并到 4 行上。`students` 沿用原型规模、`avg` 按
 * hours / students 重算 —— 聚合层按原型规模、实体层按演示规模，是 mock 的既有取舍。
 */
export const colleges = [
  { college: '计算机学院', hours: 24860, students: 1240, avg: 20.0 },
  { college: '外国语学院', hours: 22340, students: 980, avg: 22.8 },
  { college: '经济管理学院', hours: 21120, students: 1120, avg: 18.9 },
  { college: '机械工程学院', hours: 18100, students: 1060, avg: 17.1 },
]

/**
 * 学院字典（`sys_dict` 里 `dict_type = 'college'` 的那一组）。
 *
 * 学院在库里不是独立表：`dict_key` 与 `dict_value` 都存学院名（这类数据没有英文码），
 * `sort` 决定下拉顺序，`status = 1` 才出现在注册页的学院下拉里。真库的种子见
 * `sql/10_base_and_test_accounts.sql`，下面这 5 条与它逐字一致，顺序也一致。
 *
 * ⚠️ 与上面那份 `colleges` 不是一回事，刻意不合并：
 *   · `colleges` 是**聚合数据**（参与 verify:mock 的「学院时长合计 = 86,420」断言），
 *     只喂看板与首页的学院排名，**不给实体表赋值**，名字取自本清单；
 *   · 本清单是**字典数据**，管理员在学院管理页可增可删可停用，是注册页下拉的唯一数据源。
 *   `students` / `orgList` 的学院取值已收敛到本清单（见下面的 COLLEGE_TRACK），
 *   只覆盖其中 4 个：「电子信息学院」刻意留空 —— 学院管理页要能演示删除，
 *   而 verify:mock 有一条断言钉着「至少有一个未被占用的学院」。
 */
export const collegeDict = [
  { id: 1, name: '计算机学院', sort: 1, status: 1 },
  { id: 2, name: '电子信息学院', sort: 2, status: 1 },
  { id: 3, name: '经济管理学院', sort: 3, status: 1 },
  { id: 4, name: '外国语学院', sort: 4, status: 1 },
  { id: 5, name: '机械工程学院', sort: 5, status: 1 },
]

/**
 * 学生的「学院 / 专业 / 班级」三元组：三者必须同下标（`sql/04` 里记过这个坑，
 * 学院、专业、班级错配过一次）。学院名取自 `collegeDict`（= 真库 `sys_dict` 的 5 条），
 * 只覆盖其中 4 个，「电子信息学院」刻意留空（理由见上面 collegeDict 的注释）。
 *
 * 定义在 `orgList` / `students` 之前：两处都要在模块初始化时按 i 取用。
 */
const COLLEGE_TRACK = [
  { college: '计算机学院', major: '计算机科学与技术', classPrefix: '计科' },
  { college: '外国语学院', major: '英语', classPrefix: '英语' },
  { college: '经济管理学院', major: '工商管理', classPrefix: '工商' },
  { college: '机械工程学院', major: '机械工程', classPrefix: '机械' },
]

export const orgs = [
  { org: '青年志愿者协会', activities: 32, signRate: 0.94, passRate: 0.93 },
  { org: '红十字协会', activities: 28, signRate: 0.91, passRate: 0.9 },
  { org: '环保志愿者协会', activities: 24, signRate: 0.96, passRate: 0.95 },
  { org: '支教团', activities: 21, signRate: 0.89, passRate: 0.88 },
  { org: '社区服务队', activities: 18, signRate: 0.87, passRate: 0.85 },
  { org: '校园文明督导队', activities: 15, signRate: 0.93, passRate: 0.91 },
]

/**
 * 公益画像标签分布（8 类）。
 *
 * 原型只有 6 类，缺「助老服务型」「文化传播型」；而后端的标签体系是 8 类
 * （`PortraitTagEnum`，待办 B20-4 点名过），`/portraits/distribution` 返回的也是 8 类。
 * 这里补齐成 8 类，desc 逐字取自 `PortraitTagEnum.getDescription()`，
 * 保证 mock 与真实接口返回的内容完全一致。
 *
 * 人数仍是冻结值，**合计 12,480 = 报名总人数**（校验脚本会断言）。
 * 补两类时保持**类型标签合计 6,840 不变**，只是把它按各分类的活动数（types 的 value）
 * 分摊到 6 个类型标签上，因此「热心志愿者」「长期坚持型」这两个行为标签的人数没动。
 * 按人数降序排（前端取 distribution[0] 当「最大标签」）。
 */
export const profiles = [
  { tag: '热心志愿者', count: 3180, desc: '累计有效志愿时长大于 0 小时' },
  { tag: '长期坚持型', count: 2460, desc: '已完成志愿活动 ≥ 3 场' },
  { tag: '社区服务型', count: 1700, desc: '参与最多的活动分类为社区服务' },
  { tag: '环保行动型', count: 1310, desc: '参与最多的活动分类为环保公益' },
  { tag: '文化传播型', count: 1100, desc: '参与最多的活动分类为文化传播' },
  { tag: '大型活动型', count: 1030, desc: '参与最多的活动分类为大型赛事' },
  { tag: '校园服务型', count: 960, desc: '参与最多的活动分类为校园服务' },
  { tag: '助老服务型', count: 740, desc: '参与最多的活动分类为助老服务' },
]

export const audit = {
  total: 2418,
  passRate: 0.886,
  items: [
    { name: '已通过', value: 2142, tone: 'ok' },
    { name: '待审核', value: 186, tone: 'warn' },
    { name: '已驳回', value: 90, tone: 'bad' },
  ],
}

export const signin = { rate: 0.923, signed: 2290, total: 2480, absent: 190 }

export const flow = [
  { step: '浏览活动', desc: '按类型与时间筛选，查看活动详情与剩余名额' },
  { step: '报名审核', desc: '提交报名后由组织管理员审核，结果站内通知' },
  { step: '签到签退', desc: '现场扫码签到签退，系统自动记录实际服务时长' },
  { step: '时长认证', desc: '组织提交时长 → 学校管理员审核 → 计入公益画像' },
]

export const heatmap = {
  month: '2025-03',
  days: [
    88, 96, 74, 112, 128, 104, 92,
    118, 136, 122, 98, 146, 158, 140,
    108, 126, 134, 112, 148, 162, 152,
    116, 138, 144, 120, 156, 168, 146,
    124, 142, 158,
  ],
}

/* ============================================================
   二、实体列表
   ============================================================ */

/** 活动分类，与 types 一一对应（id 与库里的 activity_category.id 一致） */
export const categories = types.map((t, i) => ({
  id: i + 1,
  name: t.name,
  code: t.code,
  activityCount: t.value,
  sort: i + 1,
  status: 'ACTIVE',
  remark: t.remark,
}))

/**
 * 志愿活动。前 6 条为原型原数据（顺序与字段一致），
 * 其后为按同一风格扩充的条目，用于支撑列表分页与筛选演示。
 *
 * 与原型的两点差异（都是待办 A12 的分类改名带出来的）：
 *   · `type` 用的是库里那套分类名，`categoryId` 与 `categories` 的 id 一一对应；
 *   · 原型里的「支教助学」在后端的 6 个分类里**没有对应项**（库里有「文化传播」而没有
 *     支教助学），三条支教类活动（id 3/10/15）按「文化/教育类」归到「文化传播」，
 *     标题与描述保留原样。若要更贴切，可把这三条改成非遗、讲解、文化展一类的内容。
 */
export const activities = [
  {
    id: 1,
    title: '社区敬老院陪伴服务',
    type: '助老服务',
    date: '2025-03-22',
    time: '09:00',
    place: '幸福里社区敬老院',
    enrolled: 46,
    capacity: 60,
    org: '青年志愿者协会',
    hours: 4,
    status: 'PUBLISHED',
    categoryId: 5,
    orgId: 1,
    deadline: '2025-03-20',
    contact: '李同学 138****2201',
    description:
      '陪伴敬老院老人聊天、协助整理内务、开展简单的文娱活动。活动前将在集合点进行 30 分钟服务规范培训，请提前 15 分钟到达。',
  },
  {
    id: 2,
    title: '校园植树节绿化行动',
    type: '环保公益',
    date: '2025-03-12',
    time: '08:30',
    place: '东校区生态园',
    enrolled: 88,
    capacity: 100,
    org: '环保志愿者协会',
    hours: 5,
    status: 'PUBLISHED',
    categoryId: 3,
    orgId: 3,
    deadline: '2025-03-10',
    contact: '王同学 138****3312',
    description:
      '参与校园绿化带补植与养护，工具与苗木由后勤处统一提供。建议穿着耐脏衣物与防滑鞋，现场提供饮用水。',
  },
  {
    id: 3,
    title: '春季支教助学计划',
    type: '文化传播',
    date: '2025-03-25',
    time: '14:00',
    place: '附属实验小学',
    enrolled: 32,
    capacity: 40,
    org: '支教团',
    hours: 6,
    status: 'PUBLISHED',
    categoryId: 6,
    orgId: 4,
    deadline: '2025-03-23',
    contact: '张同学 138****4423',
    description:
      '面向小学三至五年级开展课后作业辅导与兴趣课堂。需连续参与四周，每周二下午，报名前请确认时间可保证。',
  },
  {
    id: 4,
    title: '校运动会志愿服务',
    type: '大型赛事',
    date: '2025-04-18',
    time: '07:30',
    place: '主体育场',
    enrolled: 152,
    capacity: 180,
    org: '校园文明督导队',
    hours: 8,
    status: 'PUBLISHED',
    categoryId: 4,
    orgId: 6,
    deadline: '2025-04-15',
    contact: '陈同学 138****5534',
    description:
      '承担检录、计时、器材搬运与场地引导等岗位。全天服务，中午提供工作餐，服务时长按 8 小时计。',
  },
  {
    id: 5,
    title: '无偿献血宣传与引导',
    type: '社区服务',
    date: '2025-03-18',
    time: '10:00',
    place: '大学生活动中心',
    enrolled: 64,
    capacity: 80,
    org: '红十字协会',
    hours: 4,
    status: 'PUBLISHED',
    categoryId: 2,
    orgId: 2,
    deadline: '2025-03-17',
    contact: '刘同学 138****6645',
    description:
      '协助血站工作人员进行秩序维护、信息登记与献血后关怀。需提前了解献血常识，现场将统一发放志愿服务证。',
  },
  {
    id: 6,
    title: '图书馆图书整理志愿岗',
    type: '校园服务',
    date: '2025-03-15',
    time: '15:00',
    place: '中心图书馆',
    enrolled: 28,
    capacity: 30,
    org: '青年志愿者协会',
    hours: 3,
    status: 'PUBLISHED',
    categoryId: 1,
    orgId: 1,
    deadline: '2025-03-14',
    contact: '赵同学 138****7756',
    description:
      '按索书号整理归架图书、协助读者检索。需熟悉中图分类法基本规则，岗前由图书馆老师进行 20 分钟培训。',
  },
  {
    id: 7,
    title: '社区垃圾分类宣传',
    type: '环保公益',
    date: '2025-03-29',
    time: '09:30',
    place: '和平路社区广场',
    enrolled: 41,
    capacity: 50,
    org: '环保志愿者协会',
    hours: 4,
    status: 'PUBLISHED',
    categoryId: 3,
    orgId: 3,
    deadline: '2025-03-27',
    contact: '孙同学 138****8867',
    description: '在社区广场设置宣传点位，发放分类指引并开展互动小游戏。',
  },
  {
    id: 8,
    title: '敬老院健康监测协助',
    type: '助老服务',
    date: '2025-04-02',
    time: '08:00',
    place: '夕阳红养老服务中心',
    enrolled: 22,
    capacity: 30,
    org: '医学院志愿服务队',
    hours: 5,
    status: 'PUBLISHED',
    categoryId: 5,
    orgId: 7,
    deadline: '2025-03-31',
    contact: '周同学 138****9978',
    description: '协助医护人员为老人测量血压血糖并记录健康档案，医学相关专业优先。',
  },
  {
    id: 9,
    title: '校园开放日引导服务',
    type: '校园服务',
    date: '2025-04-12',
    time: '08:00',
    place: '校本部南门',
    enrolled: 76,
    capacity: 90,
    org: '校园文明督导队',
    hours: 6,
    status: 'PUBLISHED',
    categoryId: 1,
    orgId: 6,
    deadline: '2025-04-10',
    contact: '吴同学 139****1023',
    description: '为来访中学生及家长提供路线引导、院系介绍与咨询服务。',
  },
  {
    id: 10,
    title: '山区小学图书捐赠募集',
    type: '文化传播',
    date: '2025-04-08',
    time: '11:00',
    place: '学生活动中心一层',
    enrolled: 58,
    capacity: 60,
    org: '支教团',
    hours: 4,
    status: 'PUBLISHED',
    categoryId: 6,
    orgId: 4,
    deadline: '2025-04-06',
    contact: '郑同学 139****2134',
    description: '募集并分类整理捐赠图书，打包寄送至对口帮扶的三所山区小学。',
  },
  {
    id: 11,
    title: '春季无偿献血活动',
    type: '社区服务',
    date: '2025-04-15',
    time: '09:00',
    place: '校医院门前广场',
    enrolled: 96,
    capacity: 120,
    org: '红十字协会',
    hours: 5,
    status: 'PUBLISHED',
    categoryId: 2,
    orgId: 2,
    deadline: '2025-04-13',
    contact: '冯同学 139****3245',
    description: '协助市中心血站开展春季无偿献血，负责登记、引导与献血后观察。',
  },
  {
    id: 12,
    title: '实验室安全巡查志愿岗',
    type: '校园服务',
    date: '2025-04-20',
    time: '14:30',
    place: '理工楼群',
    enrolled: 18,
    capacity: 24,
    org: '机械工程学院志愿队',
    hours: 3,
    status: 'PUBLISHED',
    categoryId: 1,
    orgId: 8,
    deadline: '2025-04-18',
    contact: '蒋同学 139****4356',
    description: '协助实验室管理中心巡查危化品存放与消防通道，需通过安全知识测试。',
  },
  {
    id: 13,
    title: '河道清理环保行动',
    type: '环保公益',
    date: '2025-04-26',
    time: '07:30',
    place: '北护城河沿线',
    enrolled: 63,
    capacity: 70,
    org: '环保志愿者协会',
    hours: 6,
    status: 'PUBLISHED',
    categoryId: 3,
    orgId: 3,
    deadline: '2025-04-24',
    contact: '韩同学 139****5467',
    description: '清理河道两侧垃圾并记录水质观察情况，全程约 5 公里，提供手套与垃圾袋。',
  },
  {
    id: 14,
    title: '校庆晚会会务保障',
    type: '大型赛事',
    date: '2025-05-08',
    time: '13:00',
    place: '大学生礼堂',
    enrolled: 84,
    capacity: 100,
    org: '校园文明督导队',
    hours: 7,
    status: 'PUBLISHED',
    categoryId: 4,
    orgId: 6,
    deadline: '2025-05-05',
    contact: '曹同学 139****6578',
    description: '负责晚会入场引导、后台道具搬运与现场秩序维护。',
  },
  {
    id: 15,
    title: '留守儿童暑期陪伴招募',
    type: '文化传播',
    date: '2025-05-15',
    time: '10:00',
    place: '线上报名 · 暑期实地',
    enrolled: 45,
    capacity: 50,
    org: '支教团',
    hours: 8,
    status: 'PUBLISHED',
    categoryId: 6,
    orgId: 4,
    deadline: '2025-05-12',
    contact: '沈同学 139****7689',
    description: '暑期赴对口乡镇开展为期两周的陪伴与课业辅导，需通过面试与安全培训。',
  },
  {
    id: 16,
    title: '社区老年人智能手机课堂',
    type: '助老服务',
    date: '2025-05-20',
    time: '15:00',
    place: '幸福里社区活动室',
    enrolled: 36,
    capacity: 40,
    org: '青年志愿者协会',
    hours: 4,
    status: 'PUBLISHED',
    categoryId: 5,
    orgId: 1,
    deadline: '2025-05-18',
    contact: '许同学 139****8790',
    description: '一对一教学老年人使用智能手机，内容涵盖微信、健康码与防诈骗常识。',
  },
  {
    id: 17,
    title: '毕业季行李搬运互助',
    type: '校园服务',
    date: '2025-06-20',
    time: '08:30',
    place: '各学生公寓楼下',
    enrolled: 52,
    capacity: 60,
    org: '校园文明督导队',
    hours: 5,
    status: 'PUBLISHED',
    categoryId: 1,
    orgId: 6,
    deadline: '2025-06-18',
    contact: '何同学 139****9801',
    description: '为毕业生提供行李搬运与寄送协助，分时段排班。',
  },
  {
    id: 18,
    title: '世界环境日主题宣传',
    type: '环保公益',
    date: '2025-06-05',
    time: '09:00',
    place: '校园中心广场',
    enrolled: 47,
    capacity: 60,
    org: '环保志愿者协会',
    hours: 4,
    status: 'PUBLISHED',
    categoryId: 3,
    orgId: 3,
    deadline: '2025-06-03',
    contact: '吕同学 139****0912',
    description: '布展宣传世界环境日主题，组织低碳生活承诺签名活动。',
  },
  {
    id: 19,
    title: '新生报到接站服务',
    type: '大型赛事',
    date: '2025-09-01',
    time: '07:00',
    place: '火车站南广场',
    enrolled: 108,
    capacity: 120,
    org: '青年志愿者协会',
    hours: 9,
    status: 'PUBLISHED',
    categoryId: 4,
    orgId: 1,
    deadline: '2025-08-28',
    contact: '施同学 139****1023',
    description: '在火车站与长途汽车站设点接站，引导新生乘车返校。全天轮班，含往返交通。',
  },
  {
    id: 20,
    title: '军训期间医疗辅助岗',
    type: '校园服务',
    date: '2025-09-10',
    time: '07:30',
    place: '田径场医务点',
    enrolled: 24,
    capacity: 30,
    org: '医学院志愿服务队',
    hours: 6,
    status: 'PUBLISHED',
    categoryId: 1,
    orgId: 7,
    deadline: '2025-09-08',
    contact: '孔同学 139****2134',
    description: '协助校医院处理军训期间的轻微中暑、擦伤等情况，需掌握基础急救技能。',
  },
  {
    id: 21,
    title: '社区义诊志愿服务',
    type: '社区服务',
    date: '2025-09-20',
    time: '08:30',
    place: '文化路社区服务中心',
    enrolled: 39,
    capacity: 45,
    org: '医学院志愿服务队',
    hours: 5,
    status: 'PUBLISHED',
    categoryId: 2,
    orgId: 7,
    deadline: '2025-09-18',
    contact: '杨同学 139****3245',
    description: '协助医护人员开展血压血糖检测与健康咨询，发放健康宣传手册。',
  },
  {
    id: 22,
    title: '冬季送温暖物资募集',
    type: '助老服务',
    date: '2025-11-12',
    time: '10:00',
    place: '学生活动中心',
    enrolled: 55,
    capacity: 70,
    org: '红十字协会',
    hours: 4,
    status: 'PUBLISHED',
    categoryId: 5,
    orgId: 2,
    deadline: '2025-11-10',
    contact: '朱同学 139****4356',
    description: '募集御寒衣物与生活物资，分类打包后送往对口帮扶社区。',
  },
  {
    id: 23,
    title: '图书馆寒假值守志愿岗',
    type: '校园服务',
    date: '2025-12-28',
    time: '09:00',
    place: '中心图书馆',
    enrolled: 12,
    capacity: 20,
    org: '青年志愿者协会',
    hours: 6,
    status: 'DRAFT',
    categoryId: 1,
    orgId: 1,
    deadline: '2025-12-25',
    contact: '秦同学 139****5467',
    description: '寒假期间协助图书馆值守与借还服务，需保证连续在岗。',
  },
  {
    id: 24,
    title: '校园清雪除冰行动',
    type: '校园服务',
    date: '2025-12-15',
    time: '06:30',
    place: '校园主干道',
    enrolled: 0,
    capacity: 80,
    org: '校园文明督导队',
    hours: 3,
    status: 'CANCELED',
    categoryId: 1,
    orgId: 6,
    deadline: '2025-12-14',
    contact: '尤同学 139****6578',
    description: '降雪后清理主干道与台阶积雪。因当年未出现强降雪，活动取消。',
  },
]

/** 组织。orgs 的聚合指标是冻结值，这里补充详情字段 */
export const orgList = orgs.map((o, i) => ({
  id: i + 1,
  name: o.org,
  activities: o.activities,
  signRate: o.signRate,
  passRate: o.passRate,
  code: `ORG-2024-${String(i + 1).padStart(3, '0')}`,
  contact: ['李明', '王芳', '张伟', '刘洋', '陈静', '赵磊'][i] || '负责人',
  // 存完整号码，不存脱敏串 —— 库里 org_info.phone 也是完整的（13800000002）。
  // 脱敏串一旦被回填进可编辑表单就必然过不了 /^1\d{10}$/（C13 的成因）。
  phone: `138${String(22000000 + i * 111).slice(0, 8)}`,
  college: COLLEGE_TRACK[i % COLLEGE_TRACK.length].college,
  memberCount: [186, 142, 118, 96, 74, 68][i] || 60,
  foundedAt: `20${14 + i}-09-01`,
  status: i === 5 ? 'PENDING' : 'APPROVED',
  intro: `${o.org}成立于 20${14 + i} 年，累计组织志愿活动 ${o.activities} 场，是校内活跃度较高的志愿服务组织之一。`,
}))

/** 学生档案 */
const SURNAMES = [
  '陈', '林', '王', '刘', '张', '李', '黄', '周', '吴', '徐',
  '孙', '马', '朱', '胡', '郭', '何', '高', '罗', '梁', '宋',
  '郑', '谢', '韩', '唐', '冯', '于', '董', '萧', '程', '曹',
]
const GIVEN_NAMES = [
  '思远', '晓彤', '梓涵', '雨桐', '一鸣', '佳琪', '子豪', '诗涵', '俊杰', '雅静',
  '浩然', '雨欣', '天佑', '梦洁', '泽宇', '欣怡', '子轩', '嘉怡', '晨曦', '若曦',
  '明轩', '静怡', '宇航', '可欣', '博文', '嘉宁', '天成', '梦琪', '文轩', '雅婷',
]
/**
 * 60 名学生。
 *
 * 人数不能少：报名、时长、签到几张表都按学生索引取数，而单场活动的报名数
 * 高达 46 人，池子小于它就会出现「同一学生在同一活动里重复报名」的脏数据。
 * 姓名用「姓[i%30] + 名[floor(i/30)]」组合，60 个组合互不重复。
 */
export const students = Array.from({ length: 60 }, (_, i) => {
  // 姓按 i 循环，名的下标用步长 7 错位，并对后 30 人加 3 的偏移。
  // 这样既保证 60 个组合两两不同（i 与 i+30 的姓相同但名相差 3），
  // 又让相邻记录、以及筛选后的同页记录都不会出现成片的重名。
  const name =
    SURNAMES[i % SURNAMES.length] +
    GIVEN_NAMES[(i * 7 + Math.floor(i / SURNAMES.length) * 3) % GIVEN_NAMES.length]
  return {
    id: i + 1,
    name,
    studentNo: `2022${String(10001 + i * 137).slice(0, 5)}`,
    gender: i % 2 === 0 ? 'MALE' : 'FEMALE',
    college: COLLEGE_TRACK[i % COLLEGE_TRACK.length].college,
    major: COLLEGE_TRACK[i % COLLEGE_TRACK.length].major,
    grade: ['2022', '2023', '2024'][i % 3],
    className: `${COLLEGE_TRACK[i % COLLEGE_TRACK.length].classPrefix}${2201 + (i % 3)}`,
    // 完整 11 位号码，与库里 sys_user.phone（13800000003）同形态。
    // 个人中心 / 用户管理都会把它预填进表单，脱敏串会被 /^1\d{10}$/ 拒掉（C13）。
    phone: `138${String(33000000 + i * 173).slice(0, 8)}`,
    totalHours: 12 + ((i * 7) % 36) + (i % 2 ? 0.5 : 0),
    profileTag: profiles[i % profiles.length].tag,
  }
})

/** 账号。注册产生的新账号会跨刷新存活，见 _helpers.js 的说明 */
const seedUsers = [
  {
    id: 1,
    username: 'student',
    password: '123456',
    name: '陈思远',
    role: 'STUDENT',
    roleLabel: '学生',
    status: 'ACTIVE',
    studentId: 1,
    phone: '13833000000',
    email: 'chenshiyuan@example.edu',
    createdAt: '2024-09-01 09:12:00',
    lastLoginAt: '2025-03-21 08:41:00',
  },
  {
    id: 2,
    username: 'org_admin',
    password: '123456',
    name: '李明',
    role: 'ORG_ADMIN',
    roleLabel: '组织管理员',
    status: 'ACTIVE',
    orgId: 1,
    phone: '13822000000',
    email: 'liming@example.edu',
    createdAt: '2024-08-20 14:30:00',
    lastLoginAt: '2025-03-21 09:05:00',
  },
  {
    id: 3,
    username: 'admin',
    password: '123456',
    name: '赵慧敏',
    role: 'SCHOOL_ADMIN',
    roleLabel: '学校管理员',
    status: 'ACTIVE',
    phone: '13811000000',
    email: 'zhaohuimin@example.edu',
    createdAt: '2024-08-01 10:00:00',
    lastLoginAt: '2025-03-21 07:58:00',
  },
  ...students.slice(1, 9).map((student, i) => ({
    id: 4 + i,
    username: `stu2022${String(10002 + i * 137).slice(0, 5)}`,
    password: '123456',
    name: student.name,
    role: 'STUDENT',
    roleLabel: '学生',
    status: i === 7 ? 'DISABLED' : 'ACTIVE',
    studentId: student.id,
    phone: student.phone,
    email: `stu${10002 + i * 137}@example.edu`,
    createdAt: '2024-09-01 09:20:00',
    lastLoginAt: '2025-03-20 19:30:00',
  })),
]

export const users = loadPersistedUsers(seedUsers)

/** 角色 */
export const roles = [
  {
    id: 1,
    code: 'STUDENT',
    name: '学生',
    userCount: 12480,
    remark: '浏览活动、提交报名、查看本人时长与公益画像',
    perms: [
      'volunteer:activity:list',
      'volunteer:signup:create',
      'certification:duration:mine',
      'portrait:profile:mine',
    ],
  },
  {
    id: 2,
    code: 'ORG_ADMIN',
    name: '组织管理员',
    userCount: 68,
    remark: '发布活动、审核报名、管理签到、提交服务时长',
    perms: [
      'volunteer:activity:list',
      'volunteer:activity:create',
      'volunteer:activity:update',
      'volunteer:signup:audit',
      'volunteer:attendance:manage',
      'certification:duration:submit',
    ],
  },
  {
    id: 3,
    code: 'SCHOOL_ADMIN',
    name: '学校管理员',
    userCount: 12,
    remark: '审核组织资质与时长、维护用户与字典、查看全校数据看板',
    perms: [
      'system:user:list',
      'system:user:update',
      'system:role:list',
      'org:info:audit',
      'certification:duration:approve',
      'analytics:dashboard:view',
      'volunteer:category:manage',
      'system:log:list',
    ],
  },
]

/**
 * 通知公告（前 5 条为原型原数据）。
 *
 * content 是公告正文，与后端 notification.content / NotificationCreateDTO.content 对应，
 * 学生端 NotificationDetailView 会渲染它。审核结果类通知（SIGNUP / DURATION）的正文
 * 由后端按模板拼，这里给种子数据也补上，免得 mock 模式下详情页比真实后端少一块。
 */
export const notices = [
  { id: 1, title: '关于本学期志愿服务时长认证工作的通知', content: '各位同学：\n本学期志愿服务时长认证工作现已启动，请于 3 月 31 日前在「我的时长」中核对本人记录。如有遗漏，请联系所属志愿组织补录。', date: '2025-03-18', from: '校团委', top: true, type: 'SYSTEM', read: false },
  { id: 2, title: '优秀志愿者评选结果公示', content: '经各组织推荐与学校审核，本学年优秀志愿者评选结果已产生，公示期 5 个工作日。如有异议，请向学生工作处反映。', date: '2025-03-15', from: '学生工作处', top: true, type: 'SYSTEM', read: false },
  { id: 3, title: '志愿服务时长审核规则调整说明', content: '自本学期起，服务时长以签到签退时间差为准；缺签退或签到状态异常时按活动预计时长计，且不超过预计时长的 1.5 倍。', date: '2025-03-11', from: '校团委', top: false, type: 'DURATION', read: true },
  { id: 4, title: '关于新增「环保行动型」公益画像标签的通知', content: '公益画像标签体系新增「环保行动型」等类别，已参与环保类活动的同学将自动获得对应标签。', date: '2025-03-06', from: '系统管理员', top: false, type: 'SYSTEM', read: true },
  { id: 5, title: '校园志愿服务安全培训安排', content: '请本学期参与校外志愿服务的同学于 3 月 10 日前完成线上安全培训并提交回执。', date: '2025-03-02', from: '青年志愿者协会', top: false, type: 'SYSTEM', read: true },
  { id: 6, title: '您报名的「社区敬老院陪伴服务」已通过审核', content: '您报名的活动已通过组织管理员审核，请按活动时间地点准时参加，并在开始前 30 分钟内完成签到。', date: '2025-03-19', from: '青年志愿者协会', top: false, type: 'SIGNUP', read: false },
  { id: 7, title: '志愿服务时长已提交，等待学校审核', content: '您参与活动的服务时长已由组织管理员提交，等待学校管理员审核，通过后将计入累计时长。', date: '2025-03-21', from: '青年志愿者协会', top: false, type: 'DURATION', read: false },
  { id: 8, title: '关于秋季学期志愿服务项目申报的通知', content: '秋季学期志愿服务项目申报通道已开放，请各志愿组织于 3 月 20 日前提交项目计划书。', date: '2025-03-08', from: '校团委', top: false, type: 'SYSTEM', read: true },
]

/** 操作日志 */
export const logs = [
  { id: 1, operator: '赵慧敏', role: '学校管理员', action: '审核通过', module: '时长认证', target: '青年志愿者协会 3 月服务时长（32 条）', ip: '10.12.33.41', time: '2025-03-21 09:12:33', result: 'SUCCESS' },
  { id: 2, operator: '李明', role: '组织管理员', action: '发布活动', module: '志愿活动', target: '社区敬老院陪伴服务', ip: '10.12.44.18', time: '2025-03-20 16:44:02', result: 'SUCCESS' },
  { id: 3, operator: '赵慧敏', role: '学校管理员', action: '审核驳回', module: '时长认证', target: '社区服务队 3 月服务时长（2 条）', ip: '10.12.33.41', time: '2025-03-20 15:20:11', result: 'SUCCESS' },
  { id: 4, operator: '李明', role: '组织管理员', action: '审核报名', module: '活动报名', target: '社区敬老院陪伴服务（46 人通过）', ip: '10.12.44.18', time: '2025-03-20 14:02:57', result: 'SUCCESS' },
  { id: 5, operator: '赵慧敏', role: '学校管理员', action: '新增用户', module: '用户与权限', target: 'stu202210138', ip: '10.12.33.41', time: '2025-03-20 11:35:20', result: 'SUCCESS' },
  { id: 6, operator: '王芳', role: '组织管理员', action: '导出签到表', module: '签到签退', target: '校园植树节绿化行动', ip: '10.12.51.77', time: '2025-03-19 17:08:44', result: 'SUCCESS' },
  { id: 7, operator: '赵慧敏', role: '学校管理员', action: '审核组织资质', module: '志愿组织', target: '校园文明督导队', ip: '10.12.33.41', time: '2025-03-19 10:22:16', result: 'SUCCESS' },
  { id: 8, operator: '李明', role: '组织管理员', action: '登录失败', module: '认证', target: 'org_admin', ip: '10.12.44.18', time: '2025-03-18 22:41:09', result: 'FAIL' },
]

/** 报名记录（覆盖活动 1 的 46 人 + 其他活动若干） */
const signupStatuses = ['APPROVED', 'PENDING', 'REJECTED', 'COMPLETED', 'CANCELED']

/**
 * 活动 1 的报名记录数：46 条有效 + 3 条已驳回。
 * 有效数必须严格等于 activities[0].enrolled（46），因为活动卡片上的
 * 「46 / 60」就是按 enrolled 渲染的；驳回记录不计入名额。
 */
const FIRST_BATCH = 49
const FIRST_BATCH_REJECTED = 3

export const signups = Array.from({ length: FIRST_BATCH + 22 }, (_, i) => {
  // 首批发在活动 1，其余分散到其他活动。
  // 尾部刻意跳过活动 1，否则与上面那批会产生同学生 + 同活动的重复报名记录。
  const isFirstBatch = i < FIRST_BATCH
  const student = students[i % students.length]
  const act = isFirstBatch ? activities[0] : activities[1 + (i % 11)]
  const status = isFirstBatch
    ? i < FIRST_BATCH - FIRST_BATCH_REJECTED
      ? i % 11 === 0
        ? 'PENDING'
        : 'APPROVED'
      : 'REJECTED'
    : signupStatuses[i % signupStatuses.length]
  return {
    id: i + 1,
    activityId: act.id,
    activityTitle: act.title,
    activityDate: act.date,
    activityHours: act.hours,
    studentId: student.id,
    studentName: student.name,
    studentNo: student.studentNo,
    college: student.college,
    status,
    appliedAt: `${act.date} 08:${String(10 + (i % 48)).padStart(2, '0')}:00`,
    reason: i % 5 === 0 ? '希望参与社区服务，积累实践经验' : '',
    rejectReason: status === 'REJECTED' ? '名额已满，建议报名同类型其他活动' : '',
  }
})

/**
 * 演示账号（登录名 student，studentId = 1）的历史记录。
 *
 * 上面按索引取数时这名学生只会命中一两条，而它恰好是演示时第一个被点开的账号，
 * 「我的报名」「我的时长」会显得空荡。这里单独补一批跨活动的记录。
 * 注意：只挂在活动 2 及之后，避免与活动 1 那 46 条产生重复报名。
 */
const demoStudent = students[0]

signups.unshift(
  ...[
    { activityId: 6, status: 'COMPLETED' },
    { activityId: 5, status: 'APPROVED' },
    { activityId: 2, status: 'APPROVED' },
    { activityId: 16, status: 'APPROVED' },
    { activityId: 10, status: 'PENDING' },
    { activityId: 13, status: 'REJECTED' },
  ].map((spec, i) => {
    const act = activities.find((a) => a.id === spec.activityId)
    return {
      id: signups.length + i + 1,
      activityId: act.id,
      activityTitle: act.title,
      activityDate: act.date,
      activityHours: act.hours,
      studentId: demoStudent.id,
      studentName: demoStudent.name,
      studentNo: demoStudent.studentNo,
      college: demoStudent.college,
      status: spec.status,
      appliedAt: `${act.date} 08:${String(10 + i * 5).padStart(2, '0')}:00`,
      reason: i % 3 === 0 ? '希望参与社区服务，积累实践经验' : '',
      rejectReason: spec.status === 'REJECTED' ? '名额已满，建议报名同类型其他活动' : '',
    }
  }),
)

/** 签到记录（对应活动 1 的报名者） */
export const attendance = signups
  .filter((s) => s.activityId === activities[0].id && s.status !== 'REJECTED')
  .map((s, i) => {
    // 46 条记录按 40 / 3 / 2 / 1 分布，四种状态都会出现，签到管理页才有东西可看
    const status = i < 40 ? 'SIGNED_OUT' : i < 43 ? 'SIGNED_IN' : i < 45 ? 'ABNORMAL' : 'ABSENT'
    // 签到 / 签退 / 实得时长三者必须自洽：后端口径是 (签退 − 签到) / 3600
    // （AttendancePolicy，封顶活动预计时长的 1.5 倍）。原先三处各写各的 ——
    // 签到 08:45、签退 12:05（差 3 小时 20 分）却记 4 小时，答辩现场一减就露馅（C14）。
    // 现在签退时刻由「签到时刻 + 活动时长」推出，恒等于 activities[0].hours，
    // 聚合数字一个不动；分钟取同一个错位值，免得 40 行时间完全一样。
    // 活动 09:00 开始，故取 08:55~08:59 到场（活动说明也要求提前 15 分钟）。
    const inMinute = 55 + (i % 5)
    return {
      id: i + 1,
      signupId: s.id,
      activityId: s.activityId,
      activityTitle: s.activityTitle,
      studentId: s.studentId,
      studentName: s.studentName,
      studentNo: s.studentNo,
      college: s.college,
      status,
      signInAt: status === 'NOT_SIGNED' || status === 'ABSENT' ? '' : `${s.activityDate} 08:${String(inMinute).padStart(2, '0')}:00`,
      signOutAt: status === 'SIGNED_OUT' ? `${s.activityDate} ${String(8 + activities[0].hours).padStart(2, '0')}:${String(inMinute).padStart(2, '0')}:00` : '',
      hours: status === 'SIGNED_OUT' ? activities[0].hours : 0,
    }
  })

/** 服务时长提交与审核记录 */
export const durations = Array.from({ length: 56 }, (_, i) => {
  const student = students[i % students.length]
  const act = activities[i % 18]
  const status = ['APPROVED', 'APPROVED', 'APPROVED', 'PENDING_AUDIT', 'REJECTED', 'PENDING_SUBMIT'][i % 6]
  return {
    id: i + 1,
    studentId: student.id,
    studentName: student.name,
    studentNo: student.studentNo,
    college: student.college,
    activityId: act.id,
    activityTitle: act.title,
    activityType: act.type,
    orgId: act.orgId,
    orgName: act.org,
    hours: act.hours,
    serviceDate: act.date,
    status,
    submittedAt: status === 'PENDING_SUBMIT' ? '' : `${act.date} 18:${String(10 + (i % 48)).padStart(2, '0')}:00`,
    auditedAt: status === 'APPROVED' || status === 'REJECTED' ? `${act.date} 20:${String(10 + (i % 48)).padStart(2, '0')}:00` : '',
    auditor: status === 'APPROVED' || status === 'REJECTED' ? '赵慧敏' : '',
    remark: status === 'REJECTED' ? '服务时长与签到记录不符，请核对后重新提交' : '',
    proof: i % 3 === 0 ? 'service-photo.jpg' : '',
  }
})

/**
 * 演示账号的服务时长。
 *
 * 两条约束：
 *   1. 三条已通过的时长（活动 2/5/6，5 + 4 + 3）凑成 12 小时，
 *      与 students[0].totalHours 一致 —— 否则「我的时长」的汇总会
 *      和「我的公益画像」的累计时长互相打架。
 *   2. 每条时长都对应上面 demoSignups 里的一条报名记录，不凭空出现。
 */
durations.unshift(
  ...[
    { activityId: 2, status: 'APPROVED' },
    { activityId: 5, status: 'APPROVED' },
    { activityId: 6, status: 'APPROVED' },
    { activityId: 10, status: 'PENDING_AUDIT' },
    { activityId: 13, status: 'REJECTED' },
  ].map((spec, i) => {
    const act = activities.find((a) => a.id === spec.activityId)
    const settled = spec.status === 'APPROVED' || spec.status === 'REJECTED'
    return {
      id: durations.length + i + 1,
      studentId: demoStudent.id,
      studentName: demoStudent.name,
      studentNo: demoStudent.studentNo,
      college: demoStudent.college,
      activityId: act.id,
      activityTitle: act.title,
      activityType: act.type,
      orgId: act.orgId,
      orgName: act.org,
      hours: act.hours,
      serviceDate: act.date,
      status: spec.status,
      submittedAt: `${act.date} 18:20:00`,
      auditedAt: settled ? `${act.date} 20:10:00` : '',
      auditor: settled ? '赵慧敏' : '',
      remark: spec.status === 'REJECTED' ? '服务时长与签到记录不符，请核对后重新提交' : '',
      proof: i % 2 === 0 ? 'service-photo.jpg' : '',
    }
  }),
)

/**
 * 演示账号的累计时长由已通过的时长记录反算，而不是另写一个数。
 * 「我的时长」的汇总和「我的公益画像」的累计时长读的是同一个值，
 * 两处手工对账迟早会对不上，这里让记录本身成为唯一事实来源。
 * 必须放在 portraits 之前 —— 画像明细是从 students 上取的 totalHours。
 */
demoStudent.totalHours = durations
  .filter((d) => d.studentId === demoStudent.id && d.status === 'APPROVED')
  .reduce((total, d) => total + d.hours, 0)

/**
 * 等级取值（8 条循环，分布 1/2/3/2 —— 与原型的短名版本一一对应）。
 *
 * 等级名与码值逐字取自后端 `PublicWelfareLevelEnum`：库里 `student_info.public_welfare_level`
 * 存的是**完整等级名**（「五星志愿者」），接口原样返回，所以 mock 也用完整名 ——
 * 原型用的短名（「五星」）只在 mock 下能命中色调映射，接上真实接口就退化成灰色
 * （待办 B20-3 就是这么暴露的）。色调改由 `levelCode` 驱动，与文案无关。
 *
 * ⚠️ 已知差异：这里的等级是按 i 循环取的，**与 totalHours 不挂钩**（原型如此），
 * 而后端是按 `student_info.total_duration` 定档（阈值 3 / 6 / 10 / 20 / 40 小时，见该枚举）。
 * 要让 mock 与后端算法一致就得按 totalHours 定档，但那会把演示账号（12 小时）从五星降到三星，
 * 是否要改由团队定；在改之前，画像页的「等级」列与「累计时长」列可能对不上。
 */
const LEVEL_CYCLE = [
  { level: '五星志愿者', levelCode: 'FIVE_STAR' },
  { level: '四星志愿者', levelCode: 'FOUR_STAR' },
  { level: '四星志愿者', levelCode: 'FOUR_STAR' },
  { level: '三星志愿者', levelCode: 'THREE_STAR' },
  { level: '三星志愿者', levelCode: 'THREE_STAR' },
  { level: '三星志愿者', levelCode: 'THREE_STAR' },
  { level: '二星志愿者', levelCode: 'TWO_STAR' },
  { level: '二星志愿者', levelCode: 'TWO_STAR' },
]

/** 学生公益画像明细 */
export const portraits = students.map((s, i) => {
  const level = LEVEL_CYCLE[i % LEVEL_CYCLE.length]
  return {
    studentId: s.id,
    studentName: s.name,
    studentNo: s.studentNo,
    college: s.college,
    tag: profiles[i % profiles.length].tag,
    totalHours: s.totalHours,
    activityCount: [12, 11, 10, 9, 8, 7, 6, 5][i % 8],
    communityRatio: [0.68, 0.42, 0.55, 0.31, 0.62, 0.28, 0.47, 0.39][i % 8],
    environmentRatio: [0.21, 0.58, 0.18, 0.52, 0.24, 0.19, 0.56, 0.22][i % 8],
    level: level.level,
    levelCode: level.levelCode,
    generatedAt: '2025-03-21 02:00:00',
  }
})
