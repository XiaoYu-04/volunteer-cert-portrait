package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.StudentInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生档案 Mapper。
 *
 * <p>当前不需要自定义 SQL：按学院/关键字筛选用 LambdaQueryWrapper 表达。
 * 前端学生列表还要 gender / grade 两列而库里没有，见待办 B16，本轮未扩。
 */
@Mapper
public interface StudentInfoMapper extends BaseMapper<StudentInfo> {
}
