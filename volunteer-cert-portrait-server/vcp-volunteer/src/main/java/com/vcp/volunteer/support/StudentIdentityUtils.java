package com.vcp.volunteer.support;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.framework.security.AuthUtils;

/**
 * 取当前登录学生对应的学生档案 id（{@code student_info.id}）。
 *
 * <p><b>为什么需要单独一层</b>：报名与签到都要用 {@code student_info.id} 定位「本人」，
 * 而 {@link AuthUtils} 只提供 userId / roleCode / orgId 与三个角色判断，
 * 没有读取会话里 studentId 的方法（它只公开了会话键名常量）。本轮不允许改动其他模块，
 * 因此在这里按 AuthUtils 公开的键名读一次登录态；未登录仍由 AuthUtils 抛 20001，
 * 本类不复制那套异常语义。
 *
 * <p><b>为什么不采信前端传的 studentId</b>：见待办 B19。前端把 studentId 当查询参数自己传，
 * 直接采信等于任何人改一个 id 就能替别人报名、读别人的报名记录。
 *
 * <p>本类是纯函数工具，不可实例化。
 */
public final class StudentIdentityUtils {

    private StudentIdentityUtils() {
    }

    /**
     * 取当前登录学生的学生档案 id。
     *
     * @return student_info.id
     * @throws BusinessException 未登录（20001），或已登录但会话里没有学生档案 id（10003）
     */
    public static Long requireCurrentStudentId() {
        Long userId = AuthUtils.getUserId();
        SaSession session = StpUtil.getSessionByLoginId(userId, false);
        Object studentId = session == null ? null : session.get(AuthUtils.SESSION_KEY_STUDENT_ID);
        if (studentId == null) {
            // 登录时未写入 studentId：账号没有对应的学生档案，或档案是登录后才补录的
            throw new BusinessException(ErrorCodeEnum.STUDENT_NOT_FOUND, "当前账号未关联学生档案，请联系学校管理员");
        }
        return Long.valueOf(studentId.toString());
    }
}
