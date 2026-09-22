package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色关联 Mapper。
 *
 * <p>系统里两个高频跨表动作放在这里，都是"先取 id 再交给主表查询"的两步式写法：
 * 按角色筛用户、统计每个角色的账号数。之所以不写成一个 JOIN 直接出结果，
 * 是因为用户列表要走 MyBatis-Plus 的分页插件，分页插件对自定义 JOIN 的
 * 列名与 COUNT 语句有额外要求，两步查询更稳且结果一致。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 取拥有指定角色的全部用户 id。
     *
     * <p>用户列表按角色筛选时用：拿到 id 集合后作为 IN 条件交给用户表的分页查询。
     * 刻意不过滤 sys_user.deleted —— 过滤交给用户表的分页查询统一做，
     * 否则同一条件会散落两处，将来改逻辑容易漏。
     *
     * @param roleCode 角色码，如 STUDENT
     * @return 用户 id 列表；无匹配时为空列表
     */
    @Select("""
            SELECT ur.user_id
              FROM sys_user_role ur
              JOIN sys_role r ON r.id = ur.role_id
             WHERE r.role_code = #{roleCode}
            """)
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 统计指定角色下未删除的账号数。
     *
     * <p>角色管理页每个角色要显示"多少人"。必须带上 u.deleted = 0，
     * 否则逻辑删除的用户仍被计入，数字会越用越大。
     *
     * @param roleId 角色 id
     * @return 账号数
     */
    @Select("""
            SELECT COUNT(*)
              FROM sys_user_role ur
              JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0
             WHERE ur.role_id = #{roleId}
            """)
    Long countUsersByRoleId(@Param("roleId") Long roleId);
}
