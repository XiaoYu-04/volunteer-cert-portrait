package com.vcp.certification.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.vcp.certification.mapper.DurationRefMapper;
import com.vcp.certification.service.DurationScope;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.framework.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据范围解析器：把「当前登录人是谁」翻译成「能看哪些时长记录」。
 *
 * <p>时长接口是三类角色共用的（学生看自己的、组织管理员看本组织的、学校管理员看全校），
 * 因此范围解析被单独提出来给列表、详情、提交三处复用 —— 每处各写一遍迟早会漏掉一处，
 * 而漏掉的那一处就是一条越权读取别人时长的路径。
 *
 * <p>解析结果的用法：范围里的维度非空时必须作为查询条件拼进去，绝不允许
 * 「取不到范围就不加条件」（那等于放行全量）。
 */
@Component
@RequiredArgsConstructor
public class DurationScopeResolver {

    private final DurationRefMapper refMapper;

    /**
     * 解析当前登录人的数据范围。
     *
     * @return 学生返回本人档案 id、组织管理员返回本组织 id、学校管理员两个维度都为空
     * @throws BusinessException 未登录（20001）、组织管理员的会话里没有 orgId（20003）、
     *                           学生的会话里没有 studentId 且反查不到档案（10003）
     */
    public DurationScope resolve() {
        if (AuthUtils.isStudent()) {
            return new DurationScope(currentStudentId(), null);
        }
        if (AuthUtils.isOrgAdmin()) {
            Long orgId = AuthUtils.getOrgId();
            if (orgId == null) {
                // 取不到 orgId 就无从限制范围。宁可拒绝，也不能退化成"看全校数据"
                throw new BusinessException(ErrorCodeEnum.NO_PERMISSION, "当前账号未关联志愿组织，请联系学校管理员");
            }
            return new DurationScope(null, orgId);
        }
        // 学校管理员：全校数据，不设范围
        return new DurationScope(null, null);
    }

    /**
     * 取当前登录学生的档案 id。
     *
     * <p>AuthUtils 提供了 orgId 的读取方法却没有 studentId 的（本模块不允许改 vcp-framework），
     * 因此按它公开的会话键常量直接读会话；读不到时再用 sys_user.id 反查
     * student_info.user_id 兜底 —— 兜底不是多余的：登录时档案还不存在、
     * 之后才补录的场景，会话里就没有这个键。
     *
     * @return 学生档案 id
     * @throws BusinessException 会话里没有且反查不到档案（10003）
     */
    private Long currentStudentId() {
        SaSession session = StpUtil.getSession(false);
        Object value = session == null ? null : session.get(AuthUtils.SESSION_KEY_STUDENT_ID);
        if (value != null) {
            return Long.valueOf(value.toString());
        }
        Long studentId = refMapper.selectStudentIdByUserId(AuthUtils.getUserId());
        if (studentId == null) {
            throw new BusinessException(ErrorCodeEnum.STUDENT_NOT_FOUND, "当前账号没有关联学生档案，请联系学校管理员");
        }
        return studentId;
    }
}
