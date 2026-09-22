package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 学生档案响应对象。
 *
 * <p>说明：前端目前没有页面调用 /v1/system/students（api/system.js 里定义了
 * listStudents / getStudent，但没有视图使用），本类是给后续学生管理页与
 * 画像模块预留的接口，字段按前端 mock 的学生对象命名。
 *
 * <p>前端 mock 里还有 gender / grade 两列，库里没有对应列（待办 B16），
 * 因此本类暂不提供，页面若要用需先扩列。
 *
 * <p>phone 与 name 分别来自 sys_user.phone 与 sys_user.real_name，
 * 由 Service 关联查询后填充，不是 student_info 自己的列。
 */
@Data
public class StudentVO implements Serializable {

    /** 学生档案 id（student_info.id），注意不是用户 id */
    private Long id;

    /** 姓名，取自 sys_user.real_name */
    private String name;

    private String studentNo;

    private String college;

    private String major;

    private String className;

    /** 手机号，取自 sys_user.phone */
    private String phone;

    /** 累计有效志愿时长（小时），权威值在 student_info */
    private BigDecimal totalDuration;

    /** 公益等级 */
    private String publicWelfareLevel;
}
