package com.vcp.volunteer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 志愿活动实体，对应 volunteer_activity 表。
 *
 * <p><b>end_time 由服务端推导</b>：发布表单只填「活动日期 + 开始时间 + 单人服务时长」，
 * 没有结束时间输入框；而签到窗口（待办 A2）与时长封顶都依赖结束时间，
 * 因此落库时按 {@code start_time + duration} 计算，不接受前端传值。
 *
 * <p><b>signed_count 是冗余计数列</b>：报名时用
 * {@code UPDATE ... SET signed_count = signed_count + 1 WHERE id = ? AND (max_count <= 0 OR signed_count < max_count)}
 * 原子更新（待办 A3 的口径），影响行数为 0 即名额已满，绝不先查后写 ——
 * 库里没有 version 列，乐观锁用不了，先查后写必然超卖。
 *
 * <p>{@code deadline}（报名截止）与 {@code contact}（联系方式）两列由 {@code sql/06} 补列脚本追加。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("volunteer_activity")
public class VolunteerActivity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名称 */
    private String title;

    /** 活动分类 → activity_category.id */
    private Long categoryId;

    /** 发布组织 → org_info.id */
    private Long orgId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间，由开始时间 + 预计时长推导 */
    private LocalDateTime endTime;

    /** 活动地点 */
    private String location;

    /** 人数上限；0 表示不限 */
    private Integer maxCount;

    /** 已报名人数（冗余计数，报名/驳回/取消时原子维护） */
    private Integer signedCount;

    /** 预计志愿时长（小时） */
    private BigDecimal duration;

    /** 活动状态：DRAFT / PUBLISHED / CLOSED / CANCELED */
    private String status;

    /** 封面图地址，本轮未做上传，留空 */
    private String cover;

    /** 活动详情 */
    private String description;

    /** 报名截止时间 */
    private LocalDateTime deadline;

    /** 联系方式 */
    private String contact;

    @TableLogic
    private Integer deleted;
}
