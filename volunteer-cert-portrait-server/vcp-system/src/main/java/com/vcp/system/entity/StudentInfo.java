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
 * <p>前端学生列表还要 gender / grade 两列，库里没有，本轮未扩
 * （见 docs/待办清单.md 的 B16），故本实体也不含这两个字段。
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

    /** 累计有效志愿时长（小时），NUMERIC(10,1) */
    private BigDecimal totalDuration;

    /** 公益等级，当前直接存中文等级名（待办 B18） */
    private String publicWelfareLevel;

    @TableLogic
    private Integer deleted;
}
