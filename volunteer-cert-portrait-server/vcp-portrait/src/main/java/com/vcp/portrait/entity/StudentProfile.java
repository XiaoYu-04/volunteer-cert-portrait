package com.vcp.portrait.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 学生公益画像实体，对应 student_profile 表（每个学生一行）。
 *
 * <p><b>本表是画像快照，不是权威数据</b>：{@code totalDuration} 只是
 * {@code student_info.total_duration} 的副本，权威值以 student_info 为准（待办 A5），
 * 重算时按权威值重新生成。读取侧不要反向依赖它做时长排名之类的统计。
 *
 * <p><b>刻意不含 {@code deleted}</b>：student_profile 是快照表，建表脚本里没有逻辑删除列
 * （关联表/流水表一律不加，见 {@code sql/02_schema.sql} 的说明）。硬加一个 {@code deleted}
 * 字段会让 MyBatis-Plus 生成 {@code deleted = 0} 的谓词，SQL 直接报「列不存在」。
 *
 * <p>{@code tags} 是<b>逗号分隔字符串</b>（不是数组、也不是关联表，建表脚本已定），
 * 取值见 {@link com.vcp.portrait.enums.PortraitTagEnum}；一个学生可同时拥有多个标签。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_profile")
public class StudentProfile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生档案 id → student_info.id（唯一） */
    private Long studentId;

    /** 参与活动次数，按已完成（COMPLETED）的报名计 */
    private Integer totalActivities;

    /** 累计志愿时长快照（小时）；权威值在 student_info.total_duration */
    private BigDecimal totalDuration;

    /** 偏好活动类型：参与次数最多的分类名；无已完成活动时为 null */
    private String categoryPreference;

    /** 公益标签，逗号分隔（如「热心志愿者,长期坚持型,校园服务型」）；无标签时为 null */
    private String tags;

    /** 画像描述文本 */
    private String portraitDesc;
}
