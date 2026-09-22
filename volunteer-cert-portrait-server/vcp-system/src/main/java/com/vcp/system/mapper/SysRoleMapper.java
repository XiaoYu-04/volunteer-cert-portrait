package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper。角色表只有种子数据，无需自定义 SQL。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
