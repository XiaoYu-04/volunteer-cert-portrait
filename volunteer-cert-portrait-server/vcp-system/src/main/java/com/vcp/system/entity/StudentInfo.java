package com.vcp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 学生档案实体，对应 student_info 表。
 *
 * <p><b>totalDuration 是权威值</b>：student_profile.total_duration 只是画像快照，
 * 学院排名、等级判定一律以本表为准（待办 A5 的推荐口径）。
 *
 * <p>{@code gender} / {@code grade} 两列由 {@code sql/06_backend_gap_fix2.sql} 补齐
 * （此前审计记为缺口，见待办 B16）。本类先只接上 {@code grade} —— 它对应前端
 * StudentQuery 已有的筛选参数与 StudentVO 的展示字段；{@code gender} 暂无消费方，
 * 等真有页面要用时再加，避免留下无人读的死字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_info")
public class StudentInfo extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户ID，指向 sys_user.id（唯一） */
    private Long userId;

    /** 学号，唯一 */
    private String studentNo;

    private String college;

    private String major;

    /** 班级 */
    private String className;

    /** 年级，如 2022；列由 sql/06_backend_gap_fix2.sql 追加 */
    private String grade;

    /** 累计有效志愿时长（小时），NUMERIC(10,1) */
    private BigDecimal totalDuration;

    /** 公益等级，当前直接存中文等级名（待办 B18） */
    private String publicWelfareLevel;

    @TableLogic
    private Integer deleted;
}
