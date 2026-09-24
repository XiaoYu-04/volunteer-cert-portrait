package com.vcp.system.service;

import com.vcp.system.dto.ChangePasswordDTO;
import com.vcp.system.dto.LoginDTO;
import com.vcp.system.dto.ProfileUpdateDTO;
import com.vcp.system.dto.RegisterDTO;
import com.vcp.system.vo.LoginVO;
import com.vcp.system.vo.SessionVO;

/**
 * 认证服务：登录、注册、退出、查改本人资料。
 *
 * <p>这是整个后端第一个真正跑起来的业务闭环（待办 B5）——
 * 「登录 → 拿 token → 访问受保护接口」，用来验证 vcp-common 与 vcp-framework
 * 那套基础设施确实可用。
 */
public interface AuthService {

    /**
     * 账号密码登录。
     *
     * <p>成功后除返回 token 外，还会往 Sa-Token 会话里写入角色码、组织 id、
     * 学生档案 id —— 这三项是后续所有接口判定数据范围与权限的依据，
     * 缺一项就会出现「能登录但查不到自己的数据」。
     *
     * @param dto 登录参数
     * @return token 与用户信息
     * @throws com.vcp.common.exception.BusinessException 用户名或密码错误（20001）、
     *                                                    账号已停用（20002）、
     *                                                    连续失败超限被锁定（20004）
     */
    LoginVO login(LoginDTO dto);

    /**
     * 学生自助注册，注册成功后直接返回登录态。
     *
     * <p>只创建用户与「学生」角色关联，不创建学生档案 ——
     * 学号、学院、专业这些字段注册表单里没有，凭空造一个假的学号会污染学籍数据。
     * 档案由学校管理员后续补录（与前端 mock 的行为一致）。
     *
     * @param dto 注册参数
     * @return token 与用户信息
     * @throws com.vcp.common.exception.BusinessException 各项格式校验失败（含口令长度不合规）
     *                                                    或用户名/手机号重复（10001）
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 退出登录，清掉当前会话。
     */
    void logout();

    /**
     * 取当前登录用户的会话信息。
     *
     * <p>前端刷新页面后靠它用 token 换回用户信息（路由守卫里的 fetchInfo）。
     *
     * @return 当前用户信息
     * @throws com.vcp.common.exception.BusinessException 未登录（20001）
     */
    SessionVO currentUser();

    /**
     * 修改本人资料，只能改姓名、手机号、邮箱。
     *
     * @param dto 待修改的字段，为 null 或空串的字段保持不变
     * @return 修改后的用户信息
     * @throws com.vcp.common.exception.BusinessException 未登录（20001）
     */
    SessionVO updateProfile(ProfileUpdateDTO dto);

    /**
     * 修改本人登录密码。
     *
     * <p>只能改自己的：userId 取自登录态，请求体里没有 userId 字段，
     * 管理员给他人重置口令走用户管理接口。
     *
     * <p>成功后踢掉该账号的其它会话、保留发起本次修改的会话 ——
     * 口令泄露时这一步才是真正的止血，但把当前设备一起踢下线会让人误以为修改失败。
     *
     * @param dto 原密码与新密码
     * @throws com.vcp.common.exception.BusinessException 未登录或账号已不存在（20001）、
     *                                                    新密码不合规或与原密码相同（10001）、
     *                                                    原密码不正确（20005）
     */
    void changePassword(ChangePasswordDTO dto);
}
