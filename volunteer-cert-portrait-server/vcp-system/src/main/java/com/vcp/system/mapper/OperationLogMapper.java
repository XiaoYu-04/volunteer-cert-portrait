package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。日志只追加与查询，无需自定义 SQL。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
