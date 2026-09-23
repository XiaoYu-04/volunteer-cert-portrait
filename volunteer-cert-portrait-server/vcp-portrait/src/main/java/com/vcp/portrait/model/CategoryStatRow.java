package com.vcp.portrait.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 「某学生某活动分类下的已完成活动次数」投影，画像标签与画像维度都由它聚合而来。
 *
 * <p>一次查询取回一批学生的全部分类计数，Service 侧据此算出：参与活动总次数、
 * 偏好分类（次数最多，并列按分类 id 升序）、类型标签、社区/环保占比。
 * 拆成 SQL 逐项算会让同一份数据被扫四遍，且口径容易写岔。
 */
@Data
public class CategoryStatRow implements Serializable {

    /** 学生档案 id */
    private Long studentId;

    /** 活动分类 id，用于并列时打破平局（升序） */
    private Long categoryId;

    /** 活动分类名，写入 student_profile.category_preference */
    private String categoryName;

    /** 活动分类编码，类型标签的映射键（activity_category.code） */
    private String categoryCode;

    /** 该分类下已完成的活动次数（按 activity_id 去重） */
    private Integer activityCount;
}
