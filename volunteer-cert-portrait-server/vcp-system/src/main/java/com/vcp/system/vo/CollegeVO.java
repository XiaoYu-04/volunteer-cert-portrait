package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 学院列表行（GET /api/v1/system/colleges 的返回元素）。
 *
 * <p><b>学院不是独立表</b>：库里是 {@code sys_dict} 中 {@code dict_type = 'college'}
 * 的字典行，本对象是把字典行与学生数、组织数拼在一起给管理页用的视图，
 * 字段名与前端 {@code views/admin/CollegeManageView.vue} 直接读取的键一一对应。
 *
 * <p>{@code name} 取 {@code dict_key}（这一类数据里它与 {@code dict_value} 同值）而不是
 * {@code dict_value}：{@code student_info.college} 与 {@code org_info.college} 存的是
 * 学院名本身，按名字统计用的也是它，取另一列会在两列被改得不一致时静默对不上数。
 *
 * <p>{@code status} 是 1 启用 / 0 停用，保持与 {@code sys_dict.status} 同口径的整数，
 * 没有像用户状态那样翻成 ACTIVE / DISABLED：学院的启停与账号启停是两码事，
 * 前端也是直接按数字判断的（见 mock 里的 {@code Number(body.status) === 0}）。
 */
@Data
public class CollegeVO implements Serializable {

    /** sys_dict.id */
    private Long id;

    /** 学院名，取 dict_key */
    private String name;

    /** 同类型内的排序值 */
    private Integer sort;

    /** 1 启用 / 0 停用；管理页要能看到停用项，因此列表接口不过滤该字段 */
    private Integer status;

    /** 该学院下的学生档案数（student_info，只数未逻辑删除的） */
    private Long studentCount;

    /** 该学院下挂靠的组织数，跨模块取数见 com.vcp.system.service.OrgCollegeCountPort */
    private Long orgCount;
}
