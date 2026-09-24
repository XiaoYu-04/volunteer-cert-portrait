package com.vcp.system.service;

import com.vcp.common.result.PageResult;
import com.vcp.system.dto.ResetPasswordDTO;
import com.vcp.system.dto.StatusUpdateDTO;
import com.vcp.system.dto.UserQuery;
import com.vcp.system.dto.UserSaveDTO;
import com.vcp.system.vo.UserVO;

/**
 * 用户管理服务：用户管理页的全部读写。
 *
 * <p>只有学校管理员会调到这里（接口层用 {@code @SaCheckPermission("system:user:*")} 把住），
 * 因此本接口不按登录人做数据范围过滤 —— 管理员看的就是全量。
 *
 * <p>出参一律是 {@link UserVO}，绝不返回 {@code SysUser} 实体：实体带着明文密码，
 * 直接序列化给前端等于把口令清单发出去。转换集中在实现里一处完成。
 */
public interface UserService {

    /**
     * 分页查询用户列表。
     *
     * @param query 查询条件，关键字同时匹配用户名与姓名
     * @return 分页结果，每条都带角色码与角色中文名
     */
    PageResult<UserVO> listUsers(UserQuery query);

    /**
     * 新增用户，并绑定角色。
     *
     * <p>不创建学生档案：表单里没有学号、学院，凭空造一个会污染学籍数据。
     * 学生登录后能正常看到空白的个人数据，等管理员补录档案。
     *
     * @param dto 新增参数
     * @throws com.vcp.common.exception.BusinessException 用户名为空或已存在、角色不存在（10001）
     */
    void createUser(UserSaveDTO dto);

    /**
     * 修改用户资料，可同时改角色。
     *
     * <p>用户名不可改：它是登录凭据，改了等于换账号，历史日志里的 username 也就对不上了。
     *
     * @param id  用户 id
     * @param dto 待修改字段
     * @throws com.vcp.common.exception.BusinessException 用户不存在（10002）、角色不存在（10001）
     */
    void updateUser(Long id, UserSaveDTO dto);

    /**
     * 启用 / 停用账号。
     *
     * <p>不允许停用自己：管理员一旦把自己的账号停掉，登录接口会以 20002 拒绝，
     * 而停用操作本身也需要登录，结果是这个系统再也进不去，只能去数据库改。
     *
     * @param id  用户 id
     * @param dto 目标状态，ACTIVE / DISABLED
     * @throws com.vcp.common.exception.BusinessException 用户不存在（10002）、状态值非法（10001）、
     *                                                    试图停用本人（10001）
     */
    void updateStatus(Long id, StatusUpdateDTO dto);

    /**
     * 管理员重置指定用户的口令。
     *
     * <p>与本人改密（{@code PUT /api/v1/auth/password}）是两个入口、两套口径：本人改密要校验旧口令，
     * 且只踢其它会话、保留当前会话；管理员重置拿不到旧口令，成功后<b>踢掉该账号全部会话</b>，
     * 旧 token 立即失效。两者语义不同，不能互相复用。
     *
     * <p>dto 里口令留空时重置为默认口令（与新增用户共用同一个常量），
     * 策略校验统一走 {@code PasswordUtils.checkPolicy}。
     *
     * @param id  用户 id
     * @param dto 新口令，留空则重置为默认口令
     * @throws com.vcp.common.exception.BusinessException 用户不存在（10002）、口令不符合策略（10001）
     */
    void resetPassword(Long id, ResetPasswordDTO dto);

    /**
     * 删除用户（逻辑删除），并清掉角色关联。
     *
     * <p>同样不允许删自己，理由与停用一致。
     *
     * @param id 用户 id
     * @throws com.vcp.common.exception.BusinessException 用户不存在（10002）、试图删除本人（10001）
     */
    void deleteUser(Long id);
}
