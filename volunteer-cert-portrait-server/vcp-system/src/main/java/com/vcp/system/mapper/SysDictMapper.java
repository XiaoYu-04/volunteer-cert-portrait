package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据字典 Mapper。整表按 dict_type + sort 读取，无需自定义 SQL。
 */
@Mapper
public interface SysDictMapper extends BaseMapper<SysDict> {
}
