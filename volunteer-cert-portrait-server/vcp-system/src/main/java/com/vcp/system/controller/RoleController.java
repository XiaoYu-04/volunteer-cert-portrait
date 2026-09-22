package com.vcp.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.common.result.R;
import com.vcp.system.service.RoleService;
import com.vcp.system.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色接口。
 *
 * <p>只读：系统采用三角色固定权限模型，角色不可增删改，因此没有写方法。
 * 用户管理页的角色下拉与角色管理页的权限清单都用这一个接口。
 */
@RestController
@RequestMapping("/api/v1/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 取全部角色。
     *
     * @return 角色列表
     */
    @GetMapping
    @SaCheckPermission("system:role:list")
    public R<List<RoleVO>> list() {
        return R.ok(roleService.listRoles());
    }
}
