package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.PageResult;
import com.vcp.common.result.R;
import com.vcp.framework.log.OperationLog;
import com.vcp.system.dto.StatusUpdateDTO;
import com.vcp.system.dto.UserQuery;
import com.vcp.system.dto.UserSaveDTO;
import com.vcp.system.service.UserService;
import com.vcp.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。
 *
 * <p>每个方法都挂了 {@code @SaCheckPermission}，权限标识与
 * {@code StpInterfaceImpl.ROLE_PERMS} 里学校管理员那一组逐字对应。
 * 权限不足时 Sa-Token 抛 NotRoleException/NotPermissionException，
 * 由全局异常处理器转成 20003，前端拦截器命中该码会清 token 跳登录 ——
 * 这是既有约定，不要在业务代码里另造一套。
 *
 * <p>所有写操作都标了 {@code @OperationLog}，module 固定为「用户与权限」。
 * <b>这个中文串必须与日志页筛选项逐字一致</b>，写错不报错但永远筛不出来。
 */
@RestController
@RequestMapping("/api/v1/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 分页查询用户列表。
     *
     * @param query 查询条件，由 Spring 从 query string 绑定
     * @return 分页结果
     */
    @GetMapping
    @SaCheckPermission("system:user:list")
    public R<PageResult<UserVO>> list(UserQuery query) {
        return R.ok(userService.listUsers(query));
    }

    /**
     * 新增用户。
     *
     * @param dto 新增参数
     * @return 空响应
     */
    @PostMapping
    @SaCheckPermission("system:user:create")
    @OperationLog(module = "用户与权限", action = "新增用户")
    public R<Void> create(@RequestBody UserSaveDTO dto) {
        userService.createUser(dto);
        return R.ok();
    }

    /**
     * 修改用户资料。
     *
     * @param id  用户 id
     * @param dto 待修改字段
     * @return 空响应
     */
    @PutMapping("/{id}")
    @SaCheckPermission("system:user:update")
    @OperationLog(module = "用户与权限", action = "修改用户")
    public R<Void> update(@PathVariable Long id, @RequestBody UserSaveDTO dto) {
        userService.updateUser(id, dto);
        return R.ok();
    }

    /**
     * 启用 / 停用账号。
     *
     * @param id  用户 id
     * @param dto 目标状态
     * @return 空响应
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("system:user:update")
    @OperationLog(module = "用户与权限", action = "启停账号")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateDTO dto) {
        userService.updateStatus(id, dto);
        return R.ok();
    }

    /**
     * 删除用户。
     *
     * @param id 用户 id
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:user:delete")
    @OperationLog(module = "用户与权限", action = "删除用户")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok();
    }
}
