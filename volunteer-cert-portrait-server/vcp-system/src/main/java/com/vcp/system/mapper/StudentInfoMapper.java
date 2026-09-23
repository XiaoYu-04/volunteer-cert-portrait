package com.vcp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.system.entity.StudentInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生档案 Mapper。
 *
 * <p>当前不需要自定义 SQL：按学院/年级/关键字筛选用 LambdaQueryWrapper 表达。
 * （gender / grade 两列由 sql/06_backend_gap_fix2.sql 补齐，此前审计记为缺口见待办 B16。）
 */
@Mapper
public interface StudentInfoMapper extends BaseMapper<StudentInfo> {
}
