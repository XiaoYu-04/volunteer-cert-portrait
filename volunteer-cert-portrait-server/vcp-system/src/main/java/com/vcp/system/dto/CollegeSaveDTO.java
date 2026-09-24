package com.vcp.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 新增学院请求体（POST /api/v1/system/colleges）。
 *
 * <p>只有 name 与 sort 两个字段：学院是 {@code sys_dict} 里的字典行，
 * 其余列（dict_type / dict_key / dict_value / tone / status）由服务端按这一类数据的
 * 固定口径补齐，不接受前端指定 —— 放开 dict_key 等于允许把学院名写成另一个样子，
 * 而 {@code student_info.college} 是按名字匹配的。
 *
 * <p>没有 id 字段，也没有对应的修改接口：学院改名会让已建档学生的 college 对不上，
 * 属于「删了重建」的动作，本次刻意不提供。
 */
@Data
public class CollegeSaveDTO implements Serializable {

    /** 学院名称，去空格后必填，最长 30 字 */
    private String name;

    /** 排序值；留空、为 0 或负数时由服务端排在现有学院之后 */
    private Integer sort;
}
