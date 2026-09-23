package com.vcp.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.org.entity.OrgInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织 Mapper。
 *
 * <p>列表筛选与逻辑删除均由 MyBatis-Plus Wrapper 处理，无需自定义 SQL。
 */
@Mapper
public interface OrgInfoMapper extends BaseMapper<OrgInfo> {
}
