package com.vcp.portrait.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vcp.portrait.entity.StudentProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 画像表 Mapper（本模块自己拥有的表）。
 *
 * <p>增删改查全部走 MyBatis-Plus 的 Wrapper 与实体方法，无需自定义 SQL；
 * 跨表聚合（学生档案、报名、活动、分类）统一放在
 * {@link PortraitAggregateMapper}，两类职责分开便于看出哪张表是谁的。
 */
@Mapper
public interface StudentProfileMapper extends BaseMapper<StudentProfile> {
}
