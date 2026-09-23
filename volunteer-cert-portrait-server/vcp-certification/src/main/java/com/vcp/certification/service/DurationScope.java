package com.vcp.certification.service;

/**
 * 时长记录的数据范围。
 *
 * <p>三个角色的可见范围完全不同，而前端把 studentId / orgId 当普通查询参数自己传
 * （mock 里条件为空就返回全量），后端必须以登录态为准，忽略请求里的同名参数 ——
 * 否则任意学生改一个 id 就能查别人的时长（待办 B19 里排在第一位的问题）。
 * 本对象就是「从登录态解析出来的唯一范围口径」，两个维度同时为空表示全校数据。
 *
 * @param studentId 学生本人档案 id（student_info.id）；非学生为 null
 * @param orgId     组织管理员所属组织 id；非组织管理员为 null
 */
public record DurationScope(Long studentId, Long orgId) {

    /**
     * 是否为全校范围（学校管理员）。
     *
     * @return 两个维度都不设限时返回 true
     */
    public boolean schoolWide() {
        return studentId == null && orgId == null;
    }
}
