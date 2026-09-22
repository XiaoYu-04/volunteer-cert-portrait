package com.vcp.system.service;

import com.vcp.system.vo.RoleVO;

import java.util.List;

/**
 * 角色服务。
 *
 * <p>本系统是三角色固定权限模型，角色<b>不可增删改</b>，因此这里只有一个只读方法。
 * 将来若真的要放开角色维护，新增的是写接口，不是改这个方法。
 */
public interface RoleService {

    /**
     * 取全部角色，供角色管理页与用户管理页的角色下拉共用。
     *
     * @return 角色列表，按 id 升序（与前端展示顺序一致：学生 → 组织管理员 → 学校管理员）
     */
    List<RoleVO> listRoles();
}
