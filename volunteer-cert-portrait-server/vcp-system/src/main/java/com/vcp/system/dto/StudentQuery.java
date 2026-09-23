package com.vcp.system.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生档案查询条件（GET /api/v1/system/students）。
 *
 * <p>grade 已真正参与筛选：列由 {@code sql/06_backend_gap_fix2.sql} 补齐，
 * 筛选逻辑见 {@code StudentServiceImpl.listStudents}（此前只收下不生效，见待办 B16）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentQuery extends PageQuery {

    /** 关键字，同时匹配姓名与学号 */
    private String keyword;

    /** 学院，全等匹配 */
    private String college;

    /** 年级，全等匹配，如 2022 */
    private String grade;
}
