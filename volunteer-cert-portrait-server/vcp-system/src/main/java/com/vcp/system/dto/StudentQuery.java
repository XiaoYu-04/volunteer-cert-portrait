package com.vcp.system.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生档案查询条件（GET /api/v1/system/students）。
 *
 * <p>注意 grade 字段：库里 student_info 没有 grade 列（待办 B16），
 * 因此本参数目前收下但不起筛选作用，等扩列后再接上。
 * 保留它是为了让前端不必改动。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentQuery extends PageQuery {

    /** 关键字，同时匹配姓名与学号 */
    private String keyword;

    /** 学院，全等匹配 */
    private String college;

    /** 年级；库里暂无该列，当前不参与筛选 */
    private String grade;
}
