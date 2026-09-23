package com.vcp.portrait.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 「画像标签串 → 人数」投影，标签分布接口由它聚合而来。
 *
 * <p><b>为什么分布统计要下推到 SQL 里 GROUP BY，而不是把每行 tags 查回 Java 再数</b>：
 * 这条查询原先写的是 {@code profileMapper.selectList(...select(StudentProfile::getTags))}
 * —— 只取一列 {@code tags}。而 {@code student_profile.tags} 可空，MyBatis 的
 * {@code returnInstanceForEmptyRow} 默认是 {@code false}（本项目没有改这个开关）：某一行的
 * <b>全部映射列都是 NULL</b> 时，这一行不会映射成对象，而是直接以 {@code null} 元素进入返回的
 * {@code List} —— 于是 {@code for (StudentProfile p : list) p.getTags()} 在第一个无标签学生上
 * 就 NPE（2026-09-23 集成验证实测的 10000 报错，演示库 31 行里有 2 行 tags 为 NULL）。
 *
 * <p><b>本投影为什么不会再出现 null 元素</b>：{@code profile_count} 取 {@code COUNT(*)}，
 * 恒非空，任何一行映射出来都至少有一个非空属性，MyBatis 必定返回对象。
 * 由此得到本模块对自定义投影的统一要求：<b>每个投影至少带一个恒非空列</b>
 * （主键、外键、COUNT 都行），否则「可空列恰好全为 NULL」的行会静默变成 null 元素。
 */
@Data
public class TagCountRow implements Serializable {

    /** 标签串（逗号分隔的组合，如「热心志愿者,长期坚持型」）；SQL 已排除整行为 NULL 的学生 */
    private String tags;

    /** 该标签串对应的学生人数，恒非空 */
    private Integer profileCount;
}
