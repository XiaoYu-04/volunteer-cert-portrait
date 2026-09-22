package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>按待办 B3 的约定，每个 Mapper 接口显式标注 @Mapper，
 * 由 MyBatis-Plus 自动扫描 com.vcp 包发现，不额外配置 @MapperScan。
 *
 * <p>本接口不需要自定义 SQL：用户列表的筛选（关键字、状态）用 LambdaQueryWrapper 表达，
 * 按角色筛选与角色列的填充走 SysUserRoleMapper 的两步查询，
 * 避免把 JOIN 写进分页 SQL 而踩到 MyBatis-Plus 分页的列名冲突。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
