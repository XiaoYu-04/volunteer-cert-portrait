package com.vcp.portrait.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 画像响应对象，字段名与前端契约（{@code src/mock/data/portrait.js} 与
 * {@code src/views/{student/MyPortraitView,admin/PortraitView}.vue}）逐条对齐。
 *
 * <p>几个容易踩的点：
 * <ul>
 *   <li>累计时长叫 {@code totalHours} 而不是 {@code totalDuration} —— 前端写死的是前者；</li>
 *   <li>{@code tag} 是<b>单个</b>主标签（表格一列、印章一枚只放得下一个），
 *       {@code tags} 才是全部标签；前端只读 {@code tag}，{@code tags} 供详情与后续扩展；</li>
 *   <li>{@code generatedAt} 已在服务端格式化成 {@code yyyy-MM-dd HH:mm:ss}
 *       （前端直接渲染该字段，不做二次格式化）；</li>
 *   <li>{@code communityRatio} / {@code environmentRatio} 是 <b>0~1 的比例</b>，
 *       前端 {@code formatPercent} 会乘 100，不要在这里先乘好；</li>
 *   <li>{@code dimensions} 只有「我的画像」接口返回，列表与详情不带（与前端 mock 一致）。</li>
 * </ul>
 */
@Data
public class PortraitVO implements Serializable {

    /** 学生档案 id（student_info.id），不是用户 id */
    private Long studentId;

    /** 姓名，取自 sys_user.real_name */
    private String studentName;

    /** 学号 */
    private String studentNo;

    private String college;

    private String major;

    /** 年级（入学年份） */
    private String grade;

    /** 主标签：有类型标签时取类型标签，否则取「长期坚持型」「热心志愿者」；无标签时为 null */
    private String tag;

    /** 全部标签，按「热心志愿者 → 长期坚持型 → 类型标签」的生成顺序 */
    private List<String> tags;

    /**
     * 公益等级中文名，直接取 {@code student_info.public_welfare_level} 的入库值（如「五星志愿者」）。
     *
     * <p><b>为什么不在这里转成短名</b>：{@code PortraitView.vue} 的 {@code levelTone} 是以
     * 「五星 / 四星 / 三星 / 二星」为键的（前端 mock 里正好是这四个短名），而库里存的是完整等级名，
     * 于是等级列的色调会落到 {@code tone-mute} 灰色。后端刻意<b>不做值转换</b>：
     * 一旦接口把「五星志愿者」改写成「五星」，同一个等级在库里、在 {@code 04_demo_data.sql} 里、
     * 在接口里就有了三种写法 —— 正是待办 B18 说的「重复定义」，多一处就多一处漂移。
     * 前端要恢复色调只需两行：改用下面的 {@link #levelCode} 当键，或把键补成完整等级名。
     */
    private String level;

    /**
     * 公益等级英文码（如 {@code FIVE_STAR}），取值由
     * {@link com.vcp.portrait.enums.PublicWelfareLevelEnum} 给出。
     *
     * <p>码值是<b>稳定的机器可读标识</b>：等级中文名将来若调整措辞，或按待办 B18 迁移成
     * 「英文码 + {@code sys_dict} 翻译」，按码值做的映射都不受影响。
     */
    private String levelCode;

    /** 累计有效志愿时长（小时）；权威值来自 student_info.total_duration */
    private BigDecimal totalHours;

    /** 已完成（COMPLETED）的活动场次 */
    private Integer activityCount;

    /** 社区服务类活动占比，0~1 */
    private BigDecimal communityRatio;

    /** 环保行动类活动占比，0~1 */
    private BigDecimal environmentRatio;

    /** 偏好活动类型：参与次数最多的分类名 */
    private String categoryPreference;

    /** 画像生成时间，格式 yyyy-MM-dd HH:mm:ss */
    private String generatedAt;

    /** 画像维度得分（满分 100），仅「我的画像」返回 */
    private List<PortraitDimensionVO> dimensions;
}
